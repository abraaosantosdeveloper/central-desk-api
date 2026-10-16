package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketCreateRequest;
import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.CategoryNotFoundException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.PriorityNotFoundException;
import br.com.contabilizape.centraldesk.model.Category;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Priority;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.CategoryRepository;
import br.com.contabilizape.centraldesk.repository.PriorityRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final UUID REQUESTER_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final short MEDIUM = 2;

    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private PriorityRepository priorityRepository;

    private TicketService ticketService;

    // Category e Priority só têm construtor protegido (JPA): no teste, mocks
    private final Category hardware = category();
    private final Priority medium = priority();

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, userRepository, categoryRepository, priorityRepository);
    }

    @Test
    void opensTicketForTheLoggedUser() {
        User maria = requester(UserStatus.ACTIVE);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(hardware));
        when(priorityRepository.findById(MEDIUM)).thenReturn(Optional.of(medium));
        when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = ticketService.open(REQUESTER_ID, request("  Impressora parada  ", CATEGORY_ID, MEDIUM));

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).saveAndFlush(saved.capture());
        Ticket ticket = saved.getValue();
        assertThat(ticket.getRequester()).isSameAs(maria);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getAssignee()).isNull();
        assertThat(ticket.getTitle()).isEqualTo("Impressora parada");
        assertThat(response.requesterId()).isEqualTo(REQUESTER_ID);
        assertThat(response.category().label()).isEqualTo("Hardware");
        assertThat(response.priority().label()).isEqualTo("Média");
        assertThat(response.assigneeId()).isNull();
    }

    @Test
    void unknownCategoryIsRejected() {
        User maria = requester(UserStatus.ACTIVE);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.open(REQUESTER_ID, request("Título", CATEGORY_ID, MEDIUM)))
                .isInstanceOf(CategoryNotFoundException.class);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownPriorityIsRejected() {
        User maria = requester(UserStatus.ACTIVE);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(hardware));
        when(priorityRepository.findById((short) 9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.open(REQUESTER_ID, request("Título", CATEGORY_ID, (short) 9)))
                .isInstanceOf(PriorityNotFoundException.class);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivatedRequesterCannotOpenTickets() {
        User maria = requester(UserStatus.DEACTIVATED);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));

        assertThatThrownBy(() -> ticketService.open(REQUESTER_ID, request("Título", CATEGORY_ID, MEDIUM)))
                .isInstanceOf(AccountNotActiveException.class);
        verify(ticketRepository, never()).saveAndFlush(any());
    }

    @Test
    void requesterThatNoLongerExistsIsInvalidSession() {
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.open(REQUESTER_ID, request("Título", CATEGORY_ID, MEDIUM)))
                .isInstanceOf(InvalidSessionException.class);
    }

    // ---------- auxiliares ----------

    private static TicketCreateRequest request(String title, UUID categoryId, Short priorityId) {
        return new TicketCreateRequest(title, "A impressora mostra erro de papel.", categoryId, priorityId);
    }

    private static User requester(UserStatus status) {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash",
                mock(Department.class));
        ReflectionTestUtils.setField(user, "id", REQUESTER_ID);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private static Category category() {
        Category category = mock(Category.class, withSettings().strictness(Strictness.LENIENT));
        when(category.getId()).thenReturn(CATEGORY_ID);
        when(category.getLabel()).thenReturn("Hardware");
        when(category.getColor()).thenReturn("#EF4444");
        return category;
    }

    private static Priority priority() {
        Priority priority = mock(Priority.class, withSettings().strictness(Strictness.LENIENT));
        when(priority.getId()).thenReturn(MEDIUM);
        when(priority.getLabel()).thenReturn("Média");
        return priority;
    }
}
