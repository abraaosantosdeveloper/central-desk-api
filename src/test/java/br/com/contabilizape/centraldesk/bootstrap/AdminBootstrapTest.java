package br.com.contabilizape.centraldesk.bootstrap;

import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.model.Admin;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    private static final UUID INNOVATION_ID = UUID.fromString("e9790f1f-41df-4369-bfe7-1be4be535561");

    private static final BootstrapAdminProperties COMPLETE = new BootstrapAdminProperties(
            "Ana Administradora", "52998224725", " Ana.Admin@ContabilizaPE.com.br ", "senhaForte123");

    @Mock private AdminRepository adminRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private Validator validator;

    private AdminBootstrap bootstrap(BootstrapAdminProperties properties) {
        return new AdminBootstrap(properties, INNOVATION_ID,
                adminRepository, userRepository, departmentRepository, passwordEncoder, validator);
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(adminRepository.count()).thenReturn(1L);

        bootstrap(COMPLETE).run(null);

        verify(userRepository, never()).saveAndFlush(any());
        verify(adminRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenCredentialsAreNotConfigured() {
        when(adminRepository.count()).thenReturn(0L);
        var incomplete = new BootstrapAdminProperties("Ana Administradora", "52998224725", "", "senhaForte123");

        bootstrap(incomplete).run(null);

        verify(userRepository, never()).saveAndFlush(any());
        verify(adminRepository, never()).save(any());
    }

    @Test
    void createsFirstAdminInInnovation() {
        Department innovation = mock(Department.class);
        when(adminRepository.count()).thenReturn(0L);
        when(validator.validate(any(UserCreateRequest.class))).thenReturn(Set.of());
        when(departmentRepository.findById(INNOVATION_ID)).thenReturn(Optional.of(innovation));
        when(passwordEncoder.encode("senhaForte123")).thenReturn("hash-bcrypt");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap(COMPLETE).run(null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User admin = userCaptor.getValue();
        assertThat(admin.getEmail()).isEqualTo("ana.admin@contabilizape.com.br");
        assertThat(admin.getDepartment()).isSameAs(innovation);
        assertThat(admin.getPasswordHash()).isEqualTo("hash-bcrypt");

        ArgumentCaptor<Admin> adminCaptor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(adminCaptor.capture());
        assertThat(adminCaptor.getValue().getUser()).isSameAs(admin);
        assertThat(adminCaptor.getValue().getGrantedBy()).isNull();
    }

    @Test
    void validatesCredentialsWithTheSameRulesAsRegistration() {
        // A violação é montada antes: configurar um mock dentro do thenReturn de outro gera UnfinishedStubbingException
        ConstraintViolation<UserCreateRequest> invalidCpf = violation("cpf", "CPF inválido");
        when(adminRepository.count()).thenReturn(0L);
        when(validator.validate(any(UserCreateRequest.class))).thenReturn(Set.of(invalidCpf));

        assertThatThrownBy(() -> bootstrap(COMPLETE).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cpf")
                .hasMessageContaining("CPF inválido");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesWhenEmailIsAlreadyRegistered() {
        when(adminRepository.count()).thenReturn(0L);
        when(validator.validate(any(UserCreateRequest.class))).thenReturn(Set.of());
        when(userRepository.existsByEmail("ana.admin@contabilizape.com.br")).thenReturn(true);

        assertThatThrownBy(() -> bootstrap(COMPLETE).run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesWhenCpfIsAlreadyRegistered() {
        when(adminRepository.count()).thenReturn(0L);
        when(validator.validate(any(UserCreateRequest.class))).thenReturn(Set.of());
        when(userRepository.existsByCpf("52998224725")).thenReturn(true);

        assertThatThrownBy(() -> bootstrap(COMPLETE).run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void failsWhenInnovationDepartmentIsMissing() {
        when(adminRepository.count()).thenReturn(0L);
        when(validator.validate(any(UserCreateRequest.class))).thenReturn(Set.of());
        when(departmentRepository.findById(INNOVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bootstrap(COMPLETE).run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @SuppressWarnings("unchecked")
    private static ConstraintViolation<UserCreateRequest> violation(String field, String message) {
        ConstraintViolation<UserCreateRequest> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn(field);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }
}
