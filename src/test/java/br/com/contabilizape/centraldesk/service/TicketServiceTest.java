package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.TicketCreateRequest;
import br.com.contabilizape.centraldesk.dto.TicketFilter;
import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.dto.TicketSummaryResponse;
import br.com.contabilizape.centraldesk.dto.TicketUpdateRequest;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.CategoryNotFoundException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.PriorityNotFoundException;
import br.com.contabilizape.centraldesk.exception.TicketEditForbiddenException;
import br.com.contabilizape.centraldesk.exception.TicketNotEditableException;
import br.com.contabilizape.centraldesk.exception.TicketNotFoundException;
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
import br.com.contabilizape.centraldesk.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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

    // ---------- edição ----------

    private static final UUID TICKET_ID = UUID.randomUUID();

    @Test
    void requesterEditsOpenTicket() {
        User maria = requester(UserStatus.ACTIVE);
        Ticket ticket = new Ticket("Antigo", "Descrição antiga", hardware, medium, maria);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(hardware));
        when(priorityRepository.findById(MEDIUM)).thenReturn(Optional.of(medium));

        TicketResponse response = ticketService.edit(TICKET_ID, REQUESTER_ID,
                new TicketUpdateRequest("  Novo título ", "Nova descrição", CATEGORY_ID, MEDIUM));

        assertThat(ticket.getTitle()).isEqualTo("Novo título");
        assertThat(ticket.getDescription()).isEqualTo("Nova descrição");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.title()).isEqualTo("Novo título");
        verify(ticketRepository).flush();
    }

    @Test
    void unknownTicketIsNotFound() {
        User maria = requester(UserStatus.ACTIVE);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.edit(TICKET_ID, REQUESTER_ID, update()))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void onlyTheRequesterCanEdit() {
        User maria = requester(UserStatus.ACTIVE);
        UUID joaoId = UUID.randomUUID();
        User joao = new User("João Souza", "11144477735", "joao.souza@contabilizape.com.br", "hash", mock(Department.class));
        ReflectionTestUtils.setField(joao, "id", joaoId);
        ReflectionTestUtils.setField(joao, "status", UserStatus.ACTIVE);
        Ticket ticketOfMaria = new Ticket("Antigo", "Descrição", hardware, medium, maria);
        when(userRepository.findById(joaoId)).thenReturn(Optional.of(joao));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticketOfMaria));

        assertThatThrownBy(() -> ticketService.edit(TICKET_ID, joaoId, update()))
                .isInstanceOf(TicketEditForbiddenException.class);
        assertThat(ticketOfMaria.getTitle()).isEqualTo("Antigo");
    }

    @Test
    void ticketAlreadyAssumedCannotBeEdited() {
        User maria = requester(UserStatus.ACTIVE);
        Ticket ticket = new Ticket("Antigo", "Descrição", hardware, medium, maria);
        ReflectionTestUtils.setField(ticket, "status", TicketStatus.IN_PROGRESS);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.edit(TICKET_ID, REQUESTER_ID, update()))
                .isInstanceOf(TicketNotEditableException.class);
        assertThat(ticket.getTitle()).isEqualTo("Antigo");
    }

    @Test
    void editWithUnknownCategoryIsRejected() {
        User maria = requester(UserStatus.ACTIVE);
        Ticket ticket = new Ticket("Antigo", "Descrição", hardware, medium, maria);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.edit(TICKET_ID, REQUESTER_ID, update()))
                .isInstanceOf(CategoryNotFoundException.class);
        assertThat(ticket.getTitle()).isEqualTo("Antigo");
    }

    private static TicketUpdateRequest update() {
        return new TicketUpdateRequest("Novo título", "Nova descrição", CATEGORY_ID, MEDIUM);
    }

    // ---------- detalhes ----------

    private static final UUID OTHER_ID = UUID.fromString("7a1d9e2b-3c4f-4a5b-8c6d-9e0f1a2b3c4d");

    @Test
    void requesterSeesOwnTicket() {
        User maria = requester(UserStatus.ACTIVE);
        Ticket ticket = new Ticket("Impressora", "Sem papel", hardware, medium, maria);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        TicketResponse response = ticketService.findById(TICKET_ID, REQUESTER_ID, List.of());

        assertThat(response.title()).isEqualTo("Impressora");
        assertThat(response.description()).isEqualTo("Sem papel");
    }

    @Test
    void ticketHandlerSeesAnyTicket() {
        viewerSeesTicketOfAnotherUser(List.of(Roles.TICKET_HANDLER));
    }

    @Test
    void adminSeesAnyTicket() {
        viewerSeesTicketOfAnotherUser(List.of(Roles.ADMIN));
    }

    @Test
    void anotherCollaboratorGetsNotFound() {
        User other = user(OTHER_ID, UserStatus.ACTIVE);
        Ticket ticket = new Ticket("Impressora", "Sem papel", hardware, medium, requester(UserStatus.ACTIVE));
        when(userRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        // 404 e não 403: quem não pode ver não descobre que o chamado existe
        assertThatThrownBy(() -> ticketService.findById(TICKET_ID, OTHER_ID, List.of()))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void unknownTicketDetailsAreNotFound() {
        User maria = requester(UserStatus.ACTIVE);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.findById(TICKET_ID, REQUESTER_ID, List.of()))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void deactivatedUserCannotSeeTickets() {
        User maria = requester(UserStatus.DEACTIVATED);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));

        assertThatThrownBy(() -> ticketService.findById(TICKET_ID, REQUESTER_ID, List.of()))
                .isInstanceOf(AccountNotActiveException.class);
        verify(ticketRepository, never()).findById(any());
    }

    private void viewerSeesTicketOfAnotherUser(List<String> roles) {
        User viewer = user(OTHER_ID, UserStatus.ACTIVE);
        Ticket ticket = new Ticket("Impressora", "Sem papel", hardware, medium, requester(UserStatus.ACTIVE));
        when(userRepository.findById(OTHER_ID)).thenReturn(Optional.of(viewer));
        when(ticketRepository.findById(TICKET_ID)).thenReturn(Optional.of(ticket));

        TicketResponse response = ticketService.findById(TICKET_ID, OTHER_ID, roles);

        assertThat(response.requesterId()).isEqualTo(REQUESTER_ID);
    }

    // ---------- listagens ----------

    private static final TicketFilter NO_FILTER = new TicketFilter(null, null, null);

    @Test
    void myTicketsAreSummarizedNewestFirst() {
        User maria = requester(UserStatus.ACTIVE);
        Ticket ticket = new Ticket("Impressora", "Sem papel", hardware, medium, maria);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket)));

        PageResponse<TicketSummaryResponse> page = ticketService.listMine(REQUESTER_ID, NO_FILTER, PageRequest.of(0, 20));

        assertThat(page.content()).singleElement().satisfies(summary -> {
            assertThat(summary.title()).isEqualTo("Impressora");
            assertThat(summary.requesterName()).isEqualTo("Maria da Silva");
            assertThat(summary.category().label()).isEqualTo("Hardware");
        });
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(usedPageable().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void clientSortIsIgnoredAndPageSizeIsCapped() {
        User maria = requester(UserStatus.ACTIVE);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        ticketService.listMine(REQUESTER_ID, NO_FILTER, PageRequest.of(2, 500, Sort.by("title")));

        Pageable used = usedPageable();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(TicketService.MAX_PAGE_SIZE);
        assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void deactivatedUserCannotListTickets() {
        User maria = requester(UserStatus.DEACTIVATED);
        when(userRepository.findById(REQUESTER_ID)).thenReturn(Optional.of(maria));

        assertThatThrownBy(() -> ticketService.listQueue(REQUESTER_ID, NO_FILTER, PageRequest.of(0, 20)))
                .isInstanceOf(AccountNotActiveException.class);
        assertThatThrownBy(() -> ticketService.listAssigned(REQUESTER_ID, NO_FILTER, PageRequest.of(0, 20)))
                .isInstanceOf(AccountNotActiveException.class);
        verify(ticketRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @SuppressWarnings("unchecked")
    private Pageable usedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    // ---------- auxiliares ----------

    private static User user(UUID id, UserStatus status) {
        User user = new User("João Souza", "11144477735", "joao.souza@contabilizape.com.br", "hash",
                mock(Department.class));
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

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
