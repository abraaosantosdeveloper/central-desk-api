package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.exception.LoginRateLimitException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Limite de tentativas erradas por IP (PLANO-SEGURANCA §5.4): dificulta bloquear em massa as contas
 * de quem tem e-mail fácil de adivinhar.
 */
class LoginAttemptLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-27T12:00:00Z"));
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(policy(3), clock);

    @Test
    void refusesTheIpAfterTheLimitOfMistakes() {
        for (int i = 0; i < 3; i++) {
            limiter.ensureAllowed("203.0.113.7");
            limiter.recordFailure("203.0.113.7");
        }

        assertThatThrownBy(() -> limiter.ensureAllowed("203.0.113.7"))
                .isInstanceOf(LoginRateLimitException.class)
                .satisfies(e -> assertThat(((LoginRateLimitException) e).getRetryAfter()).isEqualTo(Duration.ofMinutes(15)));
        assertThatCode(() -> limiter.ensureAllowed("198.51.100.4")).doesNotThrowAnyException();
    }

    @Test
    void oldMistakesLeaveTheWindow() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("203.0.113.7");
        }

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> limiter.ensureAllowed("203.0.113.7")).doesNotThrowAnyException();
    }

    @Test
    void retryAfterCountsFromTheOldestMistake() {
        limiter.recordFailure("203.0.113.7");
        clock.advance(Duration.ofMinutes(10));
        limiter.recordFailure("203.0.113.7");
        limiter.recordFailure("203.0.113.7");

        assertThatThrownBy(() -> limiter.ensureAllowed("203.0.113.7"))
                .satisfies(e -> assertThat(((LoginRateLimitException) e).getRetryAfter()).isEqualTo(Duration.ofMinutes(5)));
    }

    @Test
    void unknownIpIsNotLimited() {
        assertThatCode(() -> limiter.ensureAllowed(null)).doesNotThrowAnyException();
    }

    private static AccountSecurityPolicy policy(int limit) {
        AccountSecurityPolicy policy = mock(AccountSecurityPolicy.class);
        when(policy.failedLoginsPerIpPer15Minutes()).thenReturn(limit);
        return policy;
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
