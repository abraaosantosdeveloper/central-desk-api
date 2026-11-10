package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.DepartmentNotFoundException;
import br.com.contabilizape.centraldesk.exception.OriginDepartmentChangeForbiddenException;
import br.com.contabilizape.centraldesk.exception.OriginDepartmentConflictException;
import br.com.contabilizape.centraldesk.exception.TicketNotFoundException;
import br.com.contabilizape.centraldesk.model.Category;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Priority;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** Só administradores corrigem o setor de origem do chamado (revisão da D2). */
@ExtendWith(MockitoExtension.class)
class TicketOriginDepartmentServiceTest {

    private static final UUID TICKET_ID = UUID.fromString("dddddddd-0000-4000-8000-000000000004");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID FINANCE_ID = UUID.fromString("ffffffff-0000-4000-8000-000000000006");
    private static final UUID FISCAL_ID = UUID.fromString("eeeeeeee-0000-4000-8000-000000000005");

    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private DepartmentRepository departmentRepository;

    private TicketOriginDepartmentService service;

    private final Department finance = department(FINANCE_ID);
    private final Department fiscal = department(FISCAL_ID);
    private final User admin = user(ADMIN_ID, UserStatus.ACTIVE, fiscal);

    @BeforeEach
    void setUp() {
        service = new TicketOriginDepartmentService(ticketRepository, userRepository, adminRepository, departmentRepository);
    }

    @Test
    void adminChangesTheOriginDepartmentAndNothingElseChanges() {
        Ticket ticket = ticketFrom(finance);
        User handler = user(UUID.randomUUID(), UserStatus.ACTIVE, mock(Department.class));
        ticket.assign(handler, OffsetDateTime.now(ZoneOffset.UTC));
        stubAdminTicketAndDepartment(ticket, fiscal);

        TicketResponse response = service.changeOriginDepartment(TICKET_ID, ADMIN_ID, FISCAL_ID);

        assertThat(ticket.getOriginDepartment()).isSameAs(fiscal);
        assertThat(response.originDepartment().id()).isEqualTo(FISCAL_ID);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getAssignee()).isSameAs(handler);
        verify(ticketRepository).flush();
    }

    @Test
    void adminCanAlsoCorrectTheOriginOfAClosedTicket() {
        Ticket ticket = ticketFrom(finance);
        ReflectionTestUtils.setField(ticket, "status", TicketStatus.CLOSED);
        stubAdminTicketAndDepartment(ticket, fiscal);

        service.changeOriginDepartment(TICKET_ID, ADMIN_ID, FISCAL_ID);

        assertThat(ticket.getOriginDepartment()).isSameAs(fiscal);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    // Token antigo de quem deixou de ser admin: a condição vem do banco
    @Test
    void someoneWhoIsNotAdminInTheDatabaseCannotChangeTheOrigin() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(adminRepository.existsById(ADMIN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.changeOriginDepartment(TICKET_ID, ADMIN_ID, FISCAL_ID))
                .isInstanceOf(OriginDepartmentChangeForbiddenException.class);
        verifyNoInteractions(ticketRepository, departmentRepository);
    }

    @Test
    void deactivatedAdminCannotChangeTheOrigin() {
        User deactivated = user(ADMIN_ID, UserStatus.DEACTIVATED, fiscal);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> service.changeOriginDepartment(TICKET_ID, ADMIN_ID, FISCAL_ID))
                .isInstanceOf(AccountNotActiveException.class);
        verifyNoInteractions(ticketRepository, departmentRepository);
    }

    @Test
    void missingTicketIsNotFound() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(adminRepository.existsById(ADMIN_ID)).thenReturn(true);
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeOriginDepartment(TICKET_ID, ADMIN_ID, FISCAL_ID))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void missingDepartmentIsRejectedAndTheOriginStays() {
        Ticket ticket = ticketFrom(finance);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(adminRepository.existsById(ADMIN_ID)).thenReturn(true);
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(departmentRepository.findById(FISCAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeOriginDepartment(TICKET_ID, ADMIN_ID, FISCAL_ID))
                .isInstanceOf(DepartmentNotFoundException.class);
        assertThat(ticket.getOriginDepartment()).isSameAs(finance);
        verify(ticketRepository, never()).flush();
    }

    @Test
    void sameDepartmentIsAConflict() {
        Ticket ticket = ticketFrom(finance);
        stubAdminTicketAndDepartment(ticket, finance);

        assertThatThrownBy(() -> service.changeOriginDepartment(TICKET_ID, ADMIN_ID, FINANCE_ID))
                .isInstanceOf(OriginDepartmentConflictException.class);
        verify(ticketRepository, never()).flush();
    }

    // ---------- auxiliares ----------

    private void stubAdminTicketAndDepartment(Ticket ticket, Department department) {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(adminRepository.existsById(ADMIN_ID)).thenReturn(true);
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
    }

    // A origem é o departamento do solicitante na abertura
    private Ticket ticketFrom(Department origin) {
        User requester = user(UUID.randomUUID(), UserStatus.ACTIVE, origin);
        return new Ticket("Impressora", "Sem papel", mock(Category.class), mock(Priority.class), requester,
                mock(Department.class));
    }

    private static User user(UUID id, UserStatus status, Department department) {
        User user = new User("Pessoa", "52998224725", id + "@contabilizape.com.br", "hash", department);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private static Department department(UUID id) {
        Department department = mock(Department.class, withSettings().strictness(Strictness.LENIENT));
        when(department.getId()).thenReturn(id);
        when(department.getName()).thenReturn("Setor " + id);
        return department;
    }
}
