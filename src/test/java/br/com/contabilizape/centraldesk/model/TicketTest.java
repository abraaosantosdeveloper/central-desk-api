package br.com.contabilizape.centraldesk.model;

import br.com.contabilizape.centraldesk.exception.InvalidTicketTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Máquina de estados do chamado: o status só muda pelas ações (assumir, devolver à fila, encerrar).
 */
class TicketTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 20, 12, 0, 0, 0, ZoneOffset.UTC);

    // ---------- tabela de transições ----------

    @ParameterizedTest(name = "{0} → {1}: {2}")
    @CsvSource({
            "OPEN, IN_PROGRESS, true",
            "OPEN, CLOSED, true",
            "OPEN, OPEN, false",
            "IN_PROGRESS, OPEN, true",
            "IN_PROGRESS, CLOSED, true",
            "IN_PROGRESS, IN_PROGRESS, false",
            "CLOSED, OPEN, false",
            "CLOSED, IN_PROGRESS, false",
            "CLOSED, CLOSED, false"
    })
    void allowedTransitions(TicketStatus from, TicketStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    // ---------- assumir ----------

    @Test
    void assigningAnOpenTicketStartsTheService() {
        Ticket ticket = ticket(TicketStatus.OPEN);
        User handler = mock(User.class);

        ticket.assign(handler, NOW);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getAssignee()).isSameAs(handler);
        assertThat(ticket.getAssignedAt()).isEqualTo(NOW);
    }

    @Test
    void ticketAlreadyInProgressCannotBeAssignedAgain() {
        Ticket ticket = ticket(TicketStatus.IN_PROGRESS);
        User first = mock(User.class);
        ReflectionTestUtils.setField(ticket, "assignee", first);

        assertThatThrownBy(() -> ticket.assign(mock(User.class), NOW))
                .isInstanceOf(InvalidTicketTransitionException.class);
        assertThat(ticket.getAssignee()).isSameAs(first);
    }

    @Test
    void closedTicketCannotBeAssigned() {
        Ticket ticket = ticket(TicketStatus.CLOSED);

        assertThatThrownBy(() -> ticket.assign(mock(User.class), NOW))
                .isInstanceOf(InvalidTicketTransitionException.class);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    // ---------- devolver à fila ----------

    @Test
    void returningToQueueClearsTheAssignee() {
        Ticket ticket = ticket(TicketStatus.OPEN);
        ticket.assign(mock(User.class), NOW);

        ticket.returnToQueue();

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getAssignee()).isNull();
        assertThat(ticket.getAssignedAt()).isNull();
    }

    @Test
    void closedTicketDoesNotReturnToQueue() {
        Ticket ticket = ticket(TicketStatus.CLOSED);

        assertThatThrownBy(ticket::returnToQueue).isInstanceOf(InvalidTicketTransitionException.class);
    }

    // ---------- encerrar ----------

    @Test
    void closingRecordsWhoClosedAndWhen() {
        Ticket ticket = ticket(TicketStatus.IN_PROGRESS);
        User closer = mock(User.class);

        ticket.close(closer, "Troquei a fonte.", NOW);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getClosedBy()).isSameAs(closer);
        assertThat(ticket.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void openTicketCanBeClosedWithoutAssignee() {
        Ticket ticket = ticket(TicketStatus.OPEN);

        ticket.close(mock(User.class), "Feito", NOW);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getAssignee()).isNull();
    }

    @Test
    void closedTicketCannotBeClosedAgain() {
        Ticket ticket = ticket(TicketStatus.CLOSED);
        User firstCloser = mock(User.class);
        ReflectionTestUtils.setField(ticket, "closedBy", firstCloser);

        assertThatThrownBy(() -> ticket.close(mock(User.class), "Feito", NOW))
                .isInstanceOf(InvalidTicketTransitionException.class);
        assertThat(ticket.getClosedBy()).isSameAs(firstCloser);
    }

    @Test
    void reassigningAnOpenTicketStartsTheServiceWithTheNewAssignee() {
        Ticket ticket = ticket(TicketStatus.OPEN);
        User colleague = mock(User.class);

        ticket.reassign(colleague, NOW);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getAssignee()).isSameAs(colleague);
        assertThat(ticket.getAssignedAt()).isEqualTo(NOW);
    }

    @Test
    void reassigningInProgressKeepsTheOriginalAssignedAt() {
        Ticket ticket = ticket(TicketStatus.OPEN);
        ticket.assign(mock(User.class), NOW);
        User colleague = mock(User.class);

        ticket.reassign(colleague, NOW.plusHours(3));

        assertThat(ticket.getAssignee()).isSameAs(colleague);
        assertThat(ticket.getAssignedAt()).isEqualTo(NOW);
    }

    @Test
    void closedTicketCannotBeReassigned() {
        Ticket ticket = ticket(TicketStatus.CLOSED);

        assertThatThrownBy(() -> ticket.reassign(mock(User.class), NOW))
                .isInstanceOf(InvalidTicketTransitionException.class);
    }

    // ---------- reabrir (SCRUM-208) ----------

    @Test
    void reopeningWithTheSameAssigneeGoesBackToInProgress() {
        Ticket ticket = ticket(TicketStatus.OPEN);
        User assignee = mock(User.class);
        ticket.assign(assignee, NOW);
        ticket.close(assignee, "Troquei a fonte.", NOW.plusHours(1));

        ticket.reopen(true);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(ticket.getAssignee()).isSameAs(assignee);
        assertThat(ticket.getClosedBy()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
        assertThat(ticket.getResolution()).isNull();
    }

    @Test
    void reopeningWithoutTheAssigneeGoesBackToTheQueue() {
        Ticket ticket = ticket(TicketStatus.OPEN);
        ticket.assign(mock(User.class), NOW);
        ticket.close(mock(User.class), "Feito", NOW);

        ticket.reopen(false);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getAssignee()).isNull();
        assertThat(ticket.getAssignedAt()).isNull();
    }

    @Test
    void onlyClosedTicketsAreReopened() {
        assertThatThrownBy(() -> ticket(TicketStatus.IN_PROGRESS).reopen(true))
                .isInstanceOf(InvalidTicketTransitionException.class);
        assertThatThrownBy(() -> ticket(TicketStatus.OPEN).reopen(false))
                .isInstanceOf(InvalidTicketTransitionException.class);
    }

    @Test
    void closedTicketWithoutAssigneeAlwaysGoesToTheQueue() {
        Ticket ticket = ticket(TicketStatus.OPEN);
        ticket.close(mock(User.class), "Duplicado", NOW);

        ticket.reopen(true);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    }

    private static Ticket ticket(TicketStatus status) {
        Ticket ticket = new Ticket("Impressora", "Sem papel", mock(Category.class), mock(Priority.class), mock(User.class));
        ReflectionTestUtils.setField(ticket, "status", status);
        return ticket;
    }
}
