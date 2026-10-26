package br.com.contabilizape.centraldesk.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseEmitterRegistryTest {

    private static final String HANDLER = "TICKET_HANDLER";

    private final SseEmitterRegistry registry = new SseEmitterRegistry(Duration.ofMinutes(30));
    private final UUID ana = UUID.randomUUID();
    private final UUID bia = UUID.randomUUID();

    @Test
    void heartbeatKeepsTheHealthyConnections() {
        registry.subscribe(ana, List.of());
        registry.subscribe(bia, List.of());

        registry.sendHeartbeat();

        assertThat(registry.connectionCount()).isEqualTo(2);
    }

    @Test
    void heartbeatRemovesTheBrokenConnectionAndKeepsTheGoodOne() {
        SseEmitter broken = registry.subscribe(ana, List.of());
        registry.subscribe(bia, List.of());
        // Como uma conexão que o navegador ou o proxy já derrubou: enviar nela falha
        broken.complete();

        registry.sendHeartbeat();

        assertThat(registry.connectionCount()).isEqualTo(1);
    }

    @Test
    void heartbeatWithoutConnectionsIsHarmless() {
        assertThatCode(registry::sendHeartbeat).doesNotThrowAnyException();
        assertThat(registry.connectionCount()).isZero();
    }

    @Test
    void aBrokenConnectionDoesNotStopTheEventFromReachingTheOthers() {
        SseEmitter broken = registry.subscribe(ana, List.of(HANDLER));
        registry.subscribe(bia, List.of(HANDLER));
        broken.complete();

        registry.sendToRoles(List.of(HANDLER), "queue.changed", "{}");

        assertThat(registry.connectionCount()).isEqualTo(1);
    }

    @Test
    void closeUsersCompletesAndForgetsOnlyThoseUsers() {
        SseEmitter closed = registry.subscribe(ana, List.of());
        registry.subscribe(bia, List.of());

        registry.closeUsers(Set.of(ana));

        assertThat(registry.connectionCount()).isEqualTo(1);
        assertThatThrownBy(() -> closed.send("depois de fechar")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeUsersCoversEveryTabOfTheUser() {
        registry.subscribe(ana, List.of());
        registry.subscribe(ana, List.of());
        registry.subscribe(bia, List.of());

        registry.closeUsers(Set.of(ana));

        assertThat(registry.connectionCount()).isEqualTo(1);
    }
}
