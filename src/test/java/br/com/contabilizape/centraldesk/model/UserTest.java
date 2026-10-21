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
    void onlyActiveAccountsCanBeDeactivated() {
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

    private static User user(UserStatus status) {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash",
                mock(Department.class));
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}
