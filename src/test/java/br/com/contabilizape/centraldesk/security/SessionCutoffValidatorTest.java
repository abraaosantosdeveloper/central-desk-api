package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionCutoffValidatorTest {

    private static final UUID USER_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final Instant CUTOFF = Instant.parse("2026-10-22T14:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SessionCutoffValidator validator = new SessionCutoffValidator(userRepository);

    @Test
    void tokenIssuedBeforeTheCutoffIsRejected() {
        when(userRepository.findSessionsValidFrom(USER_ID)).thenReturn(Optional.of(CUTOFF.atOffset(ZoneOffset.UTC)));

        assertThat(validator.validate(token(USER_ID.toString(), CUTOFF.minusSeconds(1))).hasErrors()).isTrue();
    }

    @Test
    void tokenIssuedAtOrAfterTheCutoffIsAccepted() {
        when(userRepository.findSessionsValidFrom(USER_ID)).thenReturn(Optional.of(CUTOFF.atOffset(ZoneOffset.UTC)));

        assertThat(validator.validate(token(USER_ID.toString(), CUTOFF)).hasErrors()).isFalse();
        assertThat(validator.validate(token(USER_ID.toString(), CUTOFF.plusSeconds(5))).hasErrors()).isFalse();
    }

    @Test
    void withoutCutoffEveryValidTokenIsAccepted() {
        when(userRepository.findSessionsValidFrom(USER_ID)).thenReturn(Optional.empty());

        assertThat(validator.validate(token(USER_ID.toString(), CUTOFF)).hasErrors()).isFalse();
    }

    @Test
    void subjectThatIsNotAUserIdIsRejected() {
        assertThat(validator.validate(token("não-é-uuid", CUTOFF)).hasErrors()).isTrue();
    }

    private static Jwt token(String subject, Instant issuedAt) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }
}
