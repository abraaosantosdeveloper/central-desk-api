package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketOverviewFilter;
import br.com.contabilizape.centraldesk.dto.TicketOverviewFilter.OverviewOrder;
import br.com.contabilizape.centraldesk.dto.TicketOverviewResponse;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.TicketOverviewForbiddenException;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * "Todos os chamados" (TC1 a TC4): quem pode, ordenação, tamanho da página e contadores. O recorte de cada
 * escopo no banco é coberto no TicketOverviewIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class TicketOverviewServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final TicketOverviewFilter NO_FILTER =
            new TicketOverviewFilter(null, null, null, null, null, null, null, null, null, null);

    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;

    private TicketOverviewService service;

    @BeforeEach
    void setUp() {
        service = new TicketOverviewService(ticketRepository, userRepository, adminRepository, ZoneId.of("America/Recife"));
    }

    @Test
    void collaboratorIsForbidden() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.overview(USER_ID, NO_FILTER, PageRequest.of(0, 20)))
                .isInstanceOf(TicketOverviewForbiddenException.class);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void inactiveAccountIsRejectedBeforeAnything() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.DEACTIVATED)));

        assertThatThrownBy(() -> service.overview(USER_ID, NO_FILTER, PageRequest.of(0, 20)))
                .isInstanceOf(AccountNotActiveException.class);
        verifyNoInteractions(ticketRepository, adminRepository);
    }

    @Test
    void unknownUserIsAnInvalidSession() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.overview(USER_ID, NO_FILTER, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void adminGetsThePageAndTheCountsOfEveryTab() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(adminRepository.existsById(USER_ID)).thenReturn(true);
        when(ticketRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(Page.empty());
        when(ticketRepository.count(anySpecification())).thenReturn(3L, 2L, 5L);

        TicketOverviewResponse response = service.overview(USER_ID, NO_FILTER, PageRequest.of(0, 20));

        assertThat(response.statusCounts()).isEqualTo(new TicketOverviewResponse.StatusCounts(10, 3, 2, 5));
        assertThat(response.content()).isEmpty();
    }

    @Test
    void coordinatorIsAllowedAndPageSizeIsCapped() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(userRepository.isCoordinator(USER_ID)).thenReturn(true);
        when(ticketRepository.findAll(anySpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.overview(USER_ID, NO_FILTER, PageRequest.of(0, 500));

        assertThat(requestedPage().getPageSize()).isEqualTo(TicketOverviewService.MAX_PAGE_SIZE);
        assertThat(requestedPage().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void ordersByPriorityOrLastUpdate() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(adminRepository.existsById(USER_ID)).thenReturn(true);
        when(ticketRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(Page.empty());

        service.overview(USER_ID, ordered(OverviewOrder.PRIORITY), PageRequest.of(0, 20));
        assertThat(requestedPage().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("priority.id"), Sort.Order.asc("createdAt")));
    }

    @Test
    void ordersByLastUpdate() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(adminRepository.existsById(USER_ID)).thenReturn(true);
        when(ticketRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(Page.empty());

        service.overview(USER_ID, ordered(OverviewOrder.UPDATED), PageRequest.of(0, 20));
        assertThat(requestedPage().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt")));
    }

    private Pageable requestedPage() {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(anySpecification(), page.capture());
        return page.getValue();
    }

    @SuppressWarnings("unchecked")
    private static Specification<Ticket> anySpecification() {
        return any(Specification.class);
    }

    private static TicketOverviewFilter ordered(OverviewOrder order) {
        return new TicketOverviewFilter(null, null, null, null, null, null, null, null, null, order);
    }

    private static User user(UserStatus status) {
        User user = new User("Paula Rocha", "11144477735", "paula.rocha@contabilizape.com.br", "hash",
                mock(Department.class));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}
