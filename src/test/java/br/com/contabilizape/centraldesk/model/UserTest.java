package br.com.contabilizape.centraldesk.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Ciclo de vida da conta: ACTIVE ⇄ DEACTIVATED → ANONYMIZED (final).
 */
class UserTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 21, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void deactivatingRecordsWhen() {
        User user = user(UserStatus.ACTIVE);

        user.deactivate(NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(user.getDeactivatedAt()).isEqualTo(NOW);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void reactivatingClearsTheDeactivationDate() {
        User user = user(UserStatus.ACTIVE);
        user.deactivate(NOW);

        user.reactivate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getDeactivatedAt()).isNull();
    }

    @Test
    void onlyActiveOrBlockedAccountsCanBeDeactivated() {
        assertThatThrownBy(() -> user(UserStatus.DEACTIVATED).deactivate(NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> user(UserStatus.ANONYMIZED).deactivate(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyDeactivatedAccountsCanBeReactivated() {
        assertThatThrownBy(() -> user(UserStatus.ACTIVE).reactivate()).isInstanceOf(IllegalStateException.class);
        // Anonimizada é final: os dados pessoais já foram apagados
        assertThatThrownBy(() -> user(UserStatus.ANONYMIZED).reactivate()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void movingToAnotherDepartment() {
        User user = user(UserStatus.ACTIVE);
        Department innovation = mock(Department.class);

        user.moveTo(innovation);

        assertThat(user.getDepartment()).isSameAs(innovation);
    }

    @Test
    void adminCorrectsCpfAndEmail() {
        User user = user(UserStatus.ACTIVE);

        user.changeCpf("11144477735");
        user.changeEmail("maria.souza@contabilizape.com.br");

        assertThat(user.getCpf()).isEqualTo("11144477735");
        assertThat(user.getEmail()).isEqualTo("maria.souza@contabilizape.com.br");
    }

    @Test
    void endingSessionsRecordsTheCutoffTruncatedToTheSecond() {
        User user = user(UserStatus.ACTIVE);

        user.endSessions(NOW.plusNanos(750_000_000));

        // O "iat" do JWT não tem fração de segundo: o corte também não pode ter
        assertThat(user.getSessionsValidFrom()).isEqualTo(NOW);
    }

    // ---------- SCRUM-200: bloqueio, verificação do e-mail e troca obrigatória de senha ----------

    @Test
    void fifthConsecutiveFailureBlocksTheAccount() {
        User user = user(UserStatus.ACTIVE);

        for (int i = 1; i <= 4; i++) {
            assertThat(user.registerFailedLogin(NOW, 5)).isFalse();
        }
        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(user.registerFailedLogin(NOW, 5)).isTrue();

        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
        assertThat(user.getBlockedAt()).isEqualTo(NOW);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        User user = user(UserStatus.ACTIVE);
        user.registerFailedLogin(NOW, 5);
        user.registerFailedLogin(NOW, 5);

        user.resetFailedLogins();

        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void onlyActiveAccountsCountFailures() {
        User blocked = user(UserStatus.BLOCKED);

        assertThatThrownBy(() -> blocked.registerFailedLogin(NOW, 5)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void confirmingTheEmailActivatesAPendingAccount() {
        User user = user(UserStatus.PENDING_VERIFICATION);

        user.confirmEmail(NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> user(UserStatus.BLOCKED).confirmEmail(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void newAccountsWaitForTheEmailConfirmation() {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash",
                mock(Department.class));

        user.awaitEmailConfirmation();

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.getEmailVerifiedAt()).isNull();
        assertThat(user.isTwoFactorEnabled()).isTrue();
    }

    @Test
    void adminResetUnblocksAndRequiresANewPassword() {
        User user = user(UserStatus.ACTIVE);
        for (int i = 0; i < 5; i++) {
            user.registerFailedLogin(NOW, 5);
        }

        user.resetPasswordByAdmin("hash-temporario", NOW.plusHours(24));

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getBlockedAt()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getPasswordHash()).isEqualTo("hash-temporario");
        assertThat(user.isPasswordChangeRequired()).isTrue();
        assertThat(user.isTemporaryPasswordExpiredAt(NOW.plusHours(23))).isFalse();
        assertThat(user.isTemporaryPasswordExpiredAt(NOW.plusHours(25))).isTrue();
    }

    @Test
    void adminCannotResetThePasswordOfDeactivatedOrPendingAccounts() {
        assertThatThrownBy(() -> user(UserStatus.DEACTIVATED).resetPasswordByAdmin("h", NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> user(UserStatus.PENDING_VERIFICATION).resetPasswordByAdmin("h", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void choosingANewPasswordEndsTheTemporaryOne() {
        User user = user(UserStatus.ACTIVE);
        user.resetPasswordByAdmin("hash-temporario", NOW.plusHours(24));

        user.changePasswordHash("hash-novo");

        assertThat(user.isPasswordChangeRequired()).isFalse();
        assertThat(user.isTemporaryPasswordExpiredAt(NOW.plusDays(30))).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void blockedAccountCanBeDeactivated() {
        User user = user(UserStatus.BLOCKED);

        user.deactivate(NOW);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
    }

    @Test
    void twoFactorCanBeTurnedOffAndOn() {
        User user = user(UserStatus.ACTIVE);

        user.disableTwoFactor();
        assertThat(user.isTwoFactorEnabled()).isFalse();
        user.enableTwoFactor();
        assertThat(user.isTwoFactorEnabled()).isTrue();
    }

    private static User user(UserStatus status) {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash",
                mock(Department.class));
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}
