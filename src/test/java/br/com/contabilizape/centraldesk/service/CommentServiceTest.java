package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.event.TicketCommentedEvent;
import br.com.contabilizape.centraldesk.dto.CommentCreateRequest;
import br.com.contabilizape.centraldesk.dto.CommentResponse;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.TicketClosedForCommentsException;
import br.com.contabilizape.centraldesk.exception.TicketNotFoundException;
import br.com.contabilizape.centraldesk.model.Category;
import br.com.contabilizape.centraldesk.model.Comment;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Priority;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.realtime.CommentCreatedEvent;
import br.com.contabilizape.centraldesk.repository.CommentRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final UUID TICKET_ID = UUID.fromString("5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final UUID REQUESTER_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final UUID HANDLER_ID = UUID.fromString("7a1d9e2b-3c4f-4a5b-8c6d-9e0f1a2b3c4d");
    private static final UUID STRANGER_ID = UUID.fromString("1b2c3d4e-5f6a-4b7c-8d9e-0f1a2b3c4d5e");

    @Mock private CommentRepository commentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CommentService commentService;

    private final User requester = user(REQUESTER_ID, "Maria da Silva", UserStatus.ACTIVE);
    private final User handler = user(HANDLER_ID, "Carlos Inovação", UserStatus.ACTIVE);

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, ticketRepository, userRepository, eventPublisher);
    }

    // ---------- comentar ----------

    @Test
    void requesterCommentsOnOwnTicket() {
        Ticket ticket = ticket(TicketStatus.OPEN, null);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommentResponse response = commentService.create(TICKET_ID, REQUESTER_ID, List.of(),
                new CommentCreateRequest("  A impressora voltou a falhar  "));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("A impressora voltou a falhar");
        assertThat(saved.getValue().getAuthor()).isSameAs(requester);
        assertThat(saved.getValue().getTicket()).isSameAs(ticket);
        assertThat(response.content()).isEqualTo("A impressora voltou a falhar");
        assertThat(response.authorName()).isEqualTo("Maria da Silva");
    }

    @Test
    void newCommentIsAnnouncedToTheTicketParticipants() {
        Ticket ticket = ticket(TicketStatus.IN_PROGRESS, handler);
        when(userRepository.findById(HANDLER_ID)).thenReturn(Optional.of(handler));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        commentService.create(TICKET_ID, HANDLER_ID, List.of(Roles.TICKET_HANDLER), new CommentCreateRequest("Estou indo aí"));

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        CommentCreatedEvent event = events.getAllValues().stream().filter(CommentCreatedEvent.class::isInstance)
                .map(CommentCreatedEvent.class::cast).findFirst().orElseThrow();
        assertThat(event.recipientIds()).containsExactlyInAnyOrder(REQUESTER_ID, HANDLER_ID);
        assertThat(event.comment().content()).isEqualTo("Estou indo aí");
        // E-mail: o outro lado no momento do comentário (o responsável comentou → o solicitante)
        TicketCommentedEvent commented = events.getAllValues().stream().filter(TicketCommentedEvent.class::isInstance)
                .map(TicketCommentedEvent.class::cast).findFirst().orElseThrow();
        assertThat(commented.authorId()).isEqualTo(HANDLER_ID);
        assertThat(commented.recipientId()).isEqualTo(REQUESTER_ID);
    }

    @Test
    void closedTicketDoesNotAcceptComments() {
        Ticket ticket = ticket(TicketStatus.CLOSED, handler);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(requester));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> commentService.create(TICKET_ID, REQUESTER_ID, List.of(), new CommentCreateRequest("Oi")))
                .isInstanceOf(TicketClosedForCommentsException.class);
        verify(commentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void whoCannotSeeTheTicketGetsNotFound() {
        User stranger = user(STRANGER_ID, "João Souza", UserStatus.ACTIVE);
        when(userRepository.findById(STRANGER_ID)).thenReturn(Optional.of(stranger));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket(TicketStatus.OPEN, null)));

        assertThatThrownBy(() -> commentService.create(TICKET_ID, STRANGER_ID, List.of(), new CommentCreateRequest("Oi")))
                .isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> commentService.list(TICKET_ID, STRANGER_ID, List.of()))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void deactivatedUserCannotComment() {
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(user(REQUESTER_ID, "Maria", UserStatus.DEACTIVATED)));

        assertThatThrownBy(() -> commentService.create(TICKET_ID, REQUESTER_ID, List.of(), new CommentCreateRequest("Oi")))
                .isInstanceOf(AccountNotActiveException.class);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void deletedUserIsInvalidSession() {
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.list(TICKET_ID, REQUESTER_ID, List.of()))
                .isInstanceOf(InvalidSessionException.class);
    }

    // ---------- ler ----------

    @Test
    void commentsOfClosedTicketCanStillBeRead() {
        Ticket ticket = ticket(TicketStatus.CLOSED, handler);
        Comment first = comment(ticket, requester, "Não liga", 2);
        Comment second = comment(ticket, handler, "Troquei a fonte", 1);
        when(userRepository.findById(HANDLER_ID)).thenReturn(Optional.of(handler));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(TICKET_ID)).thenReturn(List.of(first, second));

        List<CommentResponse> comments = commentService.list(TICKET_ID, HANDLER_ID, List.of(Roles.TICKET_HANDLER));

        assertThat(comments).extracting(CommentResponse::content).containsExactly("Não liga", "Troquei a fonte");
        assertThat(comments).extracting(CommentResponse::authorName).containsExactly("Maria da Silva", "Carlos Inovação");
    }

    // ---------- auxiliares ----------

    private Ticket ticket(TicketStatus status, User assignee) {
        Ticket ticket = new Ticket("Impressora", "Sem papel", mock(Category.class), mock(Priority.class), requester);
        ReflectionTestUtils.setField(ticket, "id", TICKET_ID);
        ReflectionTestUtils.setField(ticket, "status", status);
        ReflectionTestUtils.setField(ticket, "assignee", assignee);
        return ticket;
    }

    private static Comment comment(Ticket ticket, User author, String content, int hoursAgo) {
        Comment comment = new Comment(ticket, author, content);
        ReflectionTestUtils.setField(comment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(comment, "createdAt", OffsetDateTime.now().minusHours(hoursAgo));
        return comment;
    }

    private static User user(UUID id, String name, UserStatus status) {
        User user = new User(name, null, name.toLowerCase().replace(' ', '.') + "@contabilizape.com.br", "hash",
                mock(Department.class));
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}
