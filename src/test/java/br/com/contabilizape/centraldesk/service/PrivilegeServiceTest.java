package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.exception.LastAdminException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import br.com.contabilizape.centraldesk.model.Admin;
import br.com.contabilizape.centraldesk.model.Coordinator;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.PrivilegeLevel;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.CoordinatorRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * "Alterar privilégios" (PLANO-REATRIBUICAO-SETOR-E-TODOS-CHAMADOS §3, PV1 a PV7).
 */
@ExtendWith(MockitoExtension.class)
class PrivilegeServiceTest {

    private static final UUID INNOVATION_ID = UUID.fromString("e9790f1f-41df-4369-bfe7-1be4be535561");
    private static final UUID FINANCE_ID = UUID.fromString("cccccccc-0000-4000-8000-000000000003");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.1", "JUnit");

    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private CoordinatorRepository coordinatorRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SecurityEventService securityEvents;
    @Mock private AccountEmails accountEmails;

    private PrivilegeService service;

    private final Department innovation = department(INNOVATION_ID, "Inovação");
    private final Department finance = department(FINANCE_ID, "Financeiro");
    private final User admin = user(ADMIN_ID, innovation, UserStatus.ACTIVE);

    @BeforeEach
    void setUp() {
        AccountOffboarding offboarding = new AccountOffboarding(userRepository, adminRepository, ticketRepository,
                refreshTokenService, eventPublisher);
        service = new PrivilegeService(userRepository, adminRepository, coordinatorRepository, offboarding,
                securityEvents, accountEmails, INNOVATION_ID);
    }

    @Test
    void collaboratorBecomesCoordinatorOfTheOwnDepartment() {
        User target = user(TARGET_ID, finance, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        PrivilegeLevel previous = service.change(TARGET_ID, PrivilegeLevel.COORDINATOR, ADMIN_ID, CLIENT);

        assertThat(previous).isEqualTo(PrivilegeLevel.COLLABORATOR);
        ArgumentCaptor<Coordinator> saved = ArgumentCaptor.forClass(Coordinator.class);
        verify(coordinatorRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(target);
        assertThat(saved.getValue().getGrantedBy()).isSameAs(admin);
        verify(adminRepository, never()).save(any());
    }

    // PV1: quem vira administrador deixa de ser coordenador, e vice-versa
    @Test
    void coordinatorWhoBecomesAdminStopsBeingCoordinator() {
        User target = user(TARGET_ID, innovation, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(coordinatorRepository.existsById(TARGET_ID)).thenReturn(true);

        service.change(TARGET_ID, PrivilegeLevel.ADMIN, ADMIN_ID, CLIENT);

        verify(coordinatorRepository).deleteById(TARGET_ID);
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    void adminWhoBecomesCoordinatorStopsBeingAdmin() {
        User target = user(TARGET_ID, innovation, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(adminRepository.existsById(TARGET_ID)).thenReturn(true);
        when(userRepository.findAdminsByStatus(UserStatus.ACTIVE)).thenReturn(List.of(target, admin));

        PrivilegeLevel previous = service.change(TARGET_ID, PrivilegeLevel.COORDINATOR, ADMIN_ID, CLIENT);

        assertThat(previous).isEqualTo(PrivilegeLevel.ADMIN);
        verify(adminRepository).deleteById(TARGET_ID);
        verify(coordinatorRepository).save(any(Coordinator.class));
    }

    @Test
    void collaboratorLevelRemovesEveryPrivilege() {
        User target = user(TARGET_ID, finance, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(coordinatorRepository.existsById(TARGET_ID)).thenReturn(true);

        service.change(TARGET_ID, PrivilegeLevel.COLLABORATOR, ADMIN_ID, CLIENT);

        verify(coordinatorRepository).deleteById(TARGET_ID);
        verify(coordinatorRepository, never()).save(any());
        verify(adminRepository, never()).save(any());
    }

    // PV2
    @Test
    void adminOutsideInnovationIsRejected() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, finance, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.change(TARGET_ID, PrivilegeLevel.ADMIN, ADMIN_ID, CLIENT))
                .isInstanceOf(InvalidAccountStatusException.class)
                .hasMessageContaining("Inovação");
        verify(adminRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService, securityEvents, accountEmails);
    }

    // PV3
    @Test
    void lastActiveAdminCannotBeDemoted() {
        User target = user(TARGET_ID, innovation, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(adminRepository.existsById(TARGET_ID)).thenReturn(true);
        // Quem chamou já perdeu o acesso de admin (o token ainda vale): só sobra a conta alvo
        when(userRepository.findAdminsByStatus(UserStatus.ACTIVE)).thenReturn(List.of(target));

        assertThatThrownBy(() -> service.change(TARGET_ID, PrivilegeLevel.COLLABORATOR, ADMIN_ID, CLIENT))
                .isInstanceOf(LastAdminException.class);
        verify(adminRepository, never()).deleteById(any());
    }

    // PV4
    @Test
    void nobodyChangesTheOwnPrivilege() {
        assertThatThrownBy(() -> service.change(ADMIN_ID, PrivilegeLevel.COLLABORATOR, ADMIN_ID, CLIENT))
                .isInstanceOf(InvalidAccountStatusException.class);
        verifyNoInteractions(userRepository, adminRepository, coordinatorRepository);
    }

    @Test
    void onlyActiveAccountsHaveThePrivilegeChanged() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, finance, UserStatus.DEACTIVATED)));

        assertThatThrownBy(() -> service.change(TARGET_ID, PrivilegeLevel.COORDINATOR, ADMIN_ID, CLIENT))
                .isInstanceOf(InvalidAccountStatusException.class);
        verify(coordinatorRepository, never()).save(any());
    }

    @Test
    void unknownAccountIsNotFound() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.change(TARGET_ID, PrivilegeLevel.COORDINATOR, ADMIN_ID, CLIENT))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void askingForTheCurrentLevelChangesNothing() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, finance, UserStatus.ACTIVE)));
        when(coordinatorRepository.existsById(TARGET_ID)).thenReturn(true);

        PrivilegeLevel previous = service.change(TARGET_ID, PrivilegeLevel.COORDINATOR, ADMIN_ID, CLIENT);

        assertThat(previous).isEqualTo(PrivilegeLevel.COORDINATOR);
        verify(coordinatorRepository, never()).save(any());
        verify(coordinatorRepository, never()).deleteById(any());
        verifyNoInteractions(refreshTokenService, securityEvents, accountEmails);
    }

    // PV6 e PV7: vale na hora, fica na auditoria e a pessoa é avisada
    @Test
    void changeEndsTheSessionsIsAuditedAndNotified() {
        User target = user(TARGET_ID, finance, UserStatus.ACTIVE);
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        service.change(TARGET_ID, PrivilegeLevel.COORDINATOR, ADMIN_ID, CLIENT);

        verify(refreshTokenService).endAllSessions(target);
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(securityEvents).record(eq(SecurityEventType.PRIVILEGE_CHANGED), eq(target), eq(CLIENT), details.capture());
        assertThat(details.getValue()).contains("COLLABORATOR").contains("COORDINATOR").contains(ADMIN_ID.toString());
        verify(accountEmails).privilegeChanged(target, admin, PrivilegeLevel.COLLABORATOR, PrivilegeLevel.COORDINATOR);
    }

    @Test
    void levelIsReadFromTheDatabase() {
        when(adminRepository.existsById(TARGET_ID)).thenReturn(false);
        when(coordinatorRepository.existsById(TARGET_ID)).thenReturn(true);

        assertThat(service.levelOf(TARGET_ID)).isEqualTo(PrivilegeLevel.COORDINATOR);
        verify(securityEvents, never()).record(any(), any(), any(), anyString());
    }

    private static User user(UUID id, Department department, UserStatus status) {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash", department);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private static Department department(UUID id, String name) {
        Department department = mock(Department.class, withSettings().strictness(Strictness.LENIENT));
        when(department.getId()).thenReturn(id);
        when(department.getName()).thenReturn(name);
        return department;
    }
}
