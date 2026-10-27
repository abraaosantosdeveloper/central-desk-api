package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.exception.LoginRateLimitException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de tentativas ERRADAS de login por IP, somando todas as contas, numa janela de 15 minutos
 * (PLANO-SEGURANCA §5.4). Dificulta bloquear em massa as contas, cujos e-mails são fáceis de adivinhar.
 * Só as falhas contam: na rede da empresa todos saem pelo mesmo IP, e os acessos certos não podem
 * esgotar o limite de ninguém.
 *
 * Fica em memória, como o SSE: vale para uma instância (o CentralDesk roda com uma).
 */
@Component
public class LoginAttemptLimiter {

    static final Duration WINDOW = Duration.ofMinutes(15);
    // Proteção contra um mapa enorme (muitos IPs diferentes): acima disto, limpa as janelas vencidas
    private static final int PRUNE_THRESHOLD = 10_000;

    private final AccountSecurityPolicy policy;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(AccountSecurityPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    /** @throws LoginRateLimitException se o IP já errou o limite de vezes na janela */
    public void ensureAllowed(String ip) {
        if (ip == null) {
            return;
        }
        Deque<Instant> recent = failures.get(ip);
        if (recent == null) {
            return;
        }
        Instant now = clock.instant();
        synchronized (recent) {
            dropOld(recent, now);
            if (recent.size() >= policy.failedLoginsPerIpPer15Minutes()) {
                throw new LoginRateLimitException(Duration.between(now, recent.peekFirst().plus(WINDOW)));
            }
        }
    }

    public void recordFailure(String ip) {
        if (ip == null) {
            return;
        }
        if (failures.size() > PRUNE_THRESHOLD) {
            prune();
        }
        Deque<Instant> recent = failures.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (recent) {
            recent.addLast(clock.instant());
        }
    }

    /** Testes: o limitador vive no contexto do Spring, compartilhado entre as classes de teste. */
    public void clear() {
        failures.clear();
    }

    private void prune() {
        Instant now = clock.instant();
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                dropOld(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    private static void dropOld(Deque<Instant> recent, Instant now) {
        while (!recent.isEmpty() && !recent.peekFirst().plus(WINDOW).isAfter(now)) {
            recent.removeFirst();
        }
    }
}
