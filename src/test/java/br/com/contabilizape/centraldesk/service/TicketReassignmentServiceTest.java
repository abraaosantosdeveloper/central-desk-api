package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.event.TicketReassignedEvent;
import br.com.contabilizape.centraldesk.exception.InvalidAssigneeException;
import br.com.contabilizape.centraldesk.exception.ReassignConflictException;
import br.com.contabilizape.centraldesk.exception.ReassignForbiddenException;
import br.com.contabilizape.centraldesk.model.Category;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Priority;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class TicketReassignmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final UUID INNOVATION_ID = UUID.fromString("e9790f1f-41df-4369-bfe7-1be4be535561");
    private static final UUID TICKET_ID = UUID.fromString("dddddddd-0000-4000-8000-000000000004");
    private static final UUID REQUESTER_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID NEW_ASSIGNEE_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TicketReassignmentService service;

    private final Department innovation = department(INNOVATION_ID);
    private final User requester = user(REQUESTER_ID, UserStatus.ACTIVE, mock(Department.class));
    private final User actor = user(ACTOR_ID, UserStatus.ACTIVE, innovation);
    private final User newAssignee = user(NEW_ASSIGNEE_ID, UserStatus.ACTIVE, innovation);

    @BeforeEach
    void setUp() {
        service = new TicketReassignmentService(ticketRepository, userRepository, adminRepository, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC), INNOVATION_ID);
    }

    @Test
    void reassigningATicketInProgressPublishesTheEventWithoutFromQueue() {
        Ticket ticket = ticketInProgressWith(actor);
        stubActorTicketAndNewAssignee(ticket);

        service.reassign(TICKET_ID, ACTOR_ID, List.of(Roles.TICKET_HANDLER), NEW_ASSIGNEE_ID);

        assertThat(ticket.getAssignee()).isSameAs(newAssignee);
        TicketReassignedEvent event = published();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.actorId()).isEqualTo(ACTOR_ID);
        assertThat(event.newAssigneeId()).isEqualTo(NEW_ASSIGNEE_ID);
        assertThat(event.requesterId()).isEqualTo(REQUESTER_ID);
        assertThat(event.fromQueue()).isFalse();
    }

    @Test
    void adminReassigningATicketFromTheQueuePublishesTheEventWithFromQueue() {
        Ticket ticket = new Ticket("Impressora", "Sem papel", mock(Category.class), mock(Priority.class), requester);
        stubActorTicketAndNewAssignee(ticket);

        service.reassign(TICKET_ID, ACTOR_ID, List.of(Roles.ADMIN), NEW_ASSIGNEE_ID);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        // Para a fila isto equivale a "assumir": o contador dela cai
        assertThat(published().fromQueue()).isTrue();
    }

    @Test
    void someoneWhoIsNeitherTheAssigneeNorAdminCannotReassignAndNothingIsPublished() {
        Ticket ticket = ticketInProgressWith(newAssignee);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.reassign(TICKET_ID, ACTOR_ID, List.of(Roles.TICKET_HANDLER), NEW_ASSIGNEE_ID))
                .isInstanceOf(ReassignForbiddenException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void closedTicketCannotBeReassignedAndNothingIsPublished() {
        Ticket ticket = ticketInProgressWith(actor);
        ReflectionTestUtils.setField(ticket, "status", TicketStatus.CLOSED);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.reassign(TICKET_ID, ACTOR_ID, List.of(Roles.TICKET_HANDLER), NEW_ASSIGNEE_ID))
                .isInstanceOf(ReassignConflictException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void reassigningToTheCurrentAssigneeIsAConflictAndNothingIsPublished() {
        Ticket ticket = ticketInProgressWith(newAssignee);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.reassign(TICKET_ID, ACTOR_ID, List.of(Roles.ADMIN), NEW_ASSIGNEE_ID))
                .isInstanceOf(ReassignConflictException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deactivatedDestinationIsRejectedAndNothingIsPublished() {
        Ticket ticket = ticketInProgressWith(actor);
        User deactivated = user(NEW_ASSIGNEE_ID, UserStatus.DEACTIVATED, innovation);
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(NEW_ASSIGNEE_ID)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> service.reassign(TICKET_ID, ACTOR_ID, List.of(Roles.TICKET_HANDLER), NEW_ASSIGNEE_ID))
                .isInstanceOf(InvalidAssigneeException.class);
        assertThat(ticket.getAssignee()).isSameAs(actor);
        verifyNoInteractions(eventPublisher);
    }

    // ---------- auxiliares ----------

    private void stubActorTicketAndNewAssignee(Ticket ticket) {
        when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(ticketRepository.findByIdForUpdate(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(NEW_ASSIGNEE_ID)).thenReturn(Optional.of(newAssignee));
    }

    private TicketReassignedEvent published() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(TicketReassignedEvent.class::isInstance)
                .map(TicketReassignedEvent.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError("TicketReassignedEvent não publicado"));
    }

    private Ticket ticketInProgressWith(User assignee) {
        Ticket ticket = new Ticket("Impressora", "Sem papel", mock(Category.class), mock(Priority.class), requester);
        ticket.assign(assignee, OffsetDateTime.now(ZoneOffset.UTC));
        return ticket;
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
        return department;
    }
}
