package br.com.contabilizape.centraldesk.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Conexões SSE abertas, uma ou mais por usuário (abas e dispositivos). Fica em memória:
 * funciona com uma única instância da aplicação (limite registrado no ADR SCRUM-51).
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    static final String CONNECTED = "connected";

    private record Subscription(UUID userId, Set<String> roles, SseEmitter emitter) {
    }

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final Duration timeout;

    public SseEmitterRegistry(@Value("${app.realtime.sse-timeout}") Duration timeout) {
        this.timeout = timeout;
    }

    public SseEmitter subscribe(UUID userId, Collection<String> roles) {
        Subscription subscription = new Subscription(userId, Set.copyOf(roles), new SseEmitter(timeout.toMillis()));
        subscriptions.add(subscription);

        // Encerrada pelo navegador, pelo tempo limite ou por erro: sai da lista
        SseEmitter emitter = subscription.emitter();
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(error -> subscriptions.remove(subscription));

        // O primeiro evento confirma a conexão e faz o navegador receber os cabeçalhos na hora
        send(subscription, CONNECTED, "ok");
        return emitter;
    }

    public void sendToUsers(Collection<UUID> userIds, String eventName, Object data) {
        sendTo(subscription -> userIds.contains(subscription.userId()), eventName, data);
    }

    public void sendToRoles(Collection<String> roles, String eventName, Object data) {
        sendTo(subscription -> subscription.roles().stream().anyMatch(roles::contains), eventName, data);
    }

    private void sendTo(Predicate<Subscription> recipient, String eventName, Object data) {
        subscriptions.stream().filter(recipient).forEach(subscription -> send(subscription, eventName, data));
    }

    // Uma conexão quebrada não pode impedir as outras de receberem o evento
    private void send(Subscription subscription, String eventName, Object data) {
        try {
            subscription.emitter().send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            subscriptions.remove(subscription);
            log.debug("Conexão SSE do usuário {} encerrada: {}", subscription.userId(), e.getMessage());
        }
    }
}
