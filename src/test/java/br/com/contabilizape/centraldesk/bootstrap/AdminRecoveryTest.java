package br.com.contabilizape.centraldesk.bootstrap;

import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.service.RefreshTokenService;
import br.com.contabilizape.centraldesk.service.SecurityEventService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Recuperação de emergência (PLANO-SEGURANCA §6.4): todos os administradores bloqueados.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final String EMAIL = "ana.admin@contabilizape.com.br";

    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SecurityEventService securityEvents;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void withoutTheVariableNothingHappens() {
        recovery("", "senhaForte123").run(null);

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void blockedAdminComesBackWithTheTemporaryPassword() {
        User ana = admin(UserStatus.BLOCKED);
        when(passwordEncoder.encode("senhaForte123")).thenReturn("hash-temporario");

        recovery(" Ana.Admin@ContabilizaPE.com.br ", "senhaForte123").run(null);

        assertThat(ana.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(ana.isPasswordChangeRequired()).isTrue();
        assertThat(ana.getPasswordHash()).isEqualTo("hash-temporario");
        verify(refreshTokenService).endAllSessions(ana);
        verify(securityEvents).record(eq(SecurityEventType.ADMIN_RECOVERY), eq(ana), isNull(), anyString());
    }

    @Test
    void activeAccountIsLeftAloneIfTheVariableWasForgotten() {
        User ana = admin(UserStatus.ACTIVE);

        recovery(EMAIL, "senhaForte123").run(null);

        assertThat(ana.isPasswordChangeRequired()).isFalse();
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void accountThatIsNotAdminIsRefused() {
        admin(UserStatus.BLOCKED);
        when(adminRepository.existsById(ADMIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> recovery(EMAIL, "senhaForte123").run(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void weakPasswordIsRefused() {
        admin(UserStatus.BLOCKED);

        assertThatThrownBy(() -> recovery(EMAIL, "123").run(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> recovery(EMAIL, "").run(null)).isInstanceOf(IllegalStateException.class);
    }

    private AdminRecovery recovery(String email, String password) {
        return new AdminRecovery(email, password, userRepository, adminRepository, passwordEncoder,
                refreshTokenService, securityEvents, validator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User admin(UserStatus status) {
        User user = new User("Ana Admin", "71428793860", EMAIL, "hash", mock(Department.class));
        ReflectionTestUtils.setField(user, "id", ADMIN_ID);
        ReflectionTestUtils.setField(user, "status", status);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(adminRepository.existsById(ADMIN_ID)).thenReturn(true);
        return user;
    }
}
