package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.RefreshToken;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.realtime.SessionsEndedEvent;
import br.com.contabilizape.centraldesk.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
    private static final OffsetDateTime NOW_UTC = NOW.atOffset(ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofDays(7);
    private static final UUID USER_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final String RAW = "token-do-navegador";

    @Mock private RefreshTokenRepository repository;
    @Mock private ApplicationEventPublisher events;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, Clock.fixed(NOW, ZoneOffset.UTC), TTL, events);
    }

    // ---------- emissão ----------

    @Test
    void issuedTokenIsRandomAndOnlyItsHashIsStored() {
        User maria = user(UserStatus.ACTIVE);

        String first = service.issue(maria);
        String second = service.issue(maria);

        // 32 bytes em base64url sem padding = 43 caracteres
        assertThat(first).hasSize(43).isNotEqualTo(second);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        RefreshToken stored = saved.getAllValues().get(0);
        assertThat(stored.getTokenHash()).isEqualTo(RefreshTokenService.hash(first)).hasSize(64).isNotEqualTo(first);
        assertThat(stored.getUser()).isSameAs(maria);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW_UTC.plus(TTL));
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void hashIsSha256InHex() {
        // Vetor de teste oficial do SHA-256
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    // ---------- rotação ----------

    @Test
    void rotationRevokesCurrentTokenAndIssuesAnother() {
        User maria = user(UserStatus.ACTIVE);
        RefreshToken current = new RefreshToken(maria, RefreshTokenService.hash(RAW), NOW_UTC.plusDays(1));
        when(repository.findByTokenHash(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(current));

        RotatedRefreshToken rotated = service.rotate(RAW);

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getRevokedAt()).isEqualTo(NOW_UTC);
        assertThat(rotated.user()).isSameAs(maria);
        assertThat(rotated.rawToken()).isNotEqualTo(RAW).hasSize(43);
        verify(repository).save(any(RefreshToken.class));
    }

    @Test
    void missingTokenIsInvalidSession() {
        assertThatThrownBy(() -> service.rotate(null)).isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> service.rotate("  ")).isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void unknownTokenIsInvalidSession() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidSessionException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void expiredTokenIsInvalidSession() {
        RefreshToken expired = new RefreshToken(user(UserStatus.ACTIVE), RefreshTokenService.hash(RAW), NOW_UTC.minusSeconds(1));
        when(repository.findByTokenHash(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidSessionException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void reuseOfRevokedTokenRevokesEverySessionOfTheUser() {
        RefreshToken alreadyRotated = new RefreshToken(user(UserStatus.ACTIVE), RefreshTokenService.hash(RAW), NOW_UTC.plusDays(1));
        alreadyRotated.revoke(NOW_UTC.minusMinutes(5));
        when(repository.findByTokenHash(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(alreadyRotated));

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidSessionException.class);
        verify(repository).revokeAllActiveByUserId(USER_ID, NOW_UTC);
        verify(repository, never()).save(any());
    }

    @Test
    void inactiveAccountLosesAllSessions() {
        RefreshToken current = new RefreshToken(user(UserStatus.DEACTIVATED), RefreshTokenService.hash(RAW), NOW_UTC.plusDays(1));
        when(repository.findByTokenHash(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(AccountNotActiveException.class);
        verify(repository).revokeAllActiveByUserId(USER_ID, NOW_UTC);
        verify(repository, never()).save(any());
    }

    // ---------- revogação (logout) ----------

    @Test
    void revokeMarksTheTokenAsRevoked() {
        RefreshToken current = new RefreshToken(user(UserStatus.ACTIVE), RefreshTokenService.hash(RAW), NOW_UTC.plusDays(1));
        when(repository.findByTokenHash(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(current));

        service.revoke(RAW);

        assertThat(current.getRevokedAt()).isEqualTo(NOW_UTC);
    }

    @Test
    void revokeOfUnknownOrAlreadyRevokedTokenIsIgnored() {
        RefreshToken revoked = new RefreshToken(user(UserStatus.ACTIVE), RefreshTokenService.hash(RAW), NOW_UTC.plusDays(1));
        OffsetDateTime firstRevocation = NOW_UTC.minusHours(1);
        revoked.revoke(firstRevocation);
        when(repository.findByTokenHash(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(revoked));
        when(repository.findByTokenHash(RefreshTokenService.hash("outro"))).thenReturn(Optional.empty());

        service.revoke(RAW);
        service.revoke("outro");

        // A data da primeira revogação é preservada
        assertThat(revoked.getRevokedAt()).isEqualTo(firstRevocation);
    }

    @Test
    void revokeAllEndsEverySessionOfTheUser() {
        service.revokeAll(USER_ID);

        verify(repository).revokeAllActiveByUserId(USER_ID, NOW_UTC);
    }

    @Test
    void endAllSessionsAlsoCutsTheAccessTokensAndWarnsTheOpenStreams() {
        User maria = user(UserStatus.ACTIVE);

        service.endAllSessions(maria);

        verify(repository).revokeAllActiveByUserId(USER_ID, NOW_UTC);
        assertThat(maria.getSessionsValidFrom()).isEqualTo(NOW_UTC);
        verify(events).publishEvent(new SessionsEndedEvent(USER_ID));
    }

    private static User user(UserStatus status) {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash",
                mock(Department.class));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}
