package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.DepartmentRequestResponse;
import br.com.contabilizape.centraldesk.event.DepartmentRequestApprovedEvent;
import br.com.contabilizape.centraldesk.event.DepartmentRequestRejectedEvent;
import br.com.contabilizape.centraldesk.exception.DepartmentRequestAlreadyReviewedException;
import br.com.contabilizape.centraldesk.exception.DepartmentRequestNotFoundException;
import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.DepartmentRequest;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.realtime.UserPermissionsChangedEvent;
import br.com.contabilizape.centraldesk.repository.DepartmentRequestRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class DepartmentRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("cccccccc-0000-4000-8000-000000000003");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID REQUESTER_ID = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    @Mock private DepartmentRequestRepository departmentRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private DepartmentRequestService service;

    private final Department general = department(UUID.randomUUID(), "Geral");
    private final Department innovation = department(UUID.randomUUID(), "Inovação");

    @BeforeEach
    void setUp() {
        service = new DepartmentRequestService(departmentRequestRepository, userRepository, eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void pendingRequestsAreListedOldestFirst() {
        DepartmentRequest request = request(user(REQUESTER_ID, UserStatus.ACTIVE));
        when(departmentRequestRepository.findByStatusOrderByCreatedAtAsc(DepartmentRequestStatus.PENDING))
                .thenReturn(List.of(request));

        List<DepartmentRequestResponse> pending = service.listPending();

        assertThat(pending).singleElement().satisfies(item -> {
            assertThat(item.userName()).isEqualTo("Maria da Silva");
            assertThat(item.departmentName()).isEqualTo("Inovação");
            assertThat(item.status()).isEqualTo(DepartmentRequestStatus.PENDING);
        });
    }

    @Test
    void approvingMovesTheRequesterToInnovation() {
        User requester = user(REQUESTER_ID, UserStatus.ACTIVE);
        User admin = user(ADMIN_ID, UserStatus.ACTIVE);
        DepartmentRequest request = request(requester);
        when(departmentRequestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        DepartmentRequestResponse response = service.approve(REQUEST_ID, ADMIN_ID);

        assertThat(requester.getDepartment()).isSameAs(innovation);
        assertThat(response.status()).isEqualTo(DepartmentRequestStatus.APPROVED);
        assertThat(response.reviewedById()).isEqualTo(ADMIN_ID);
        assertThat(response.reviewedAt()).isEqualTo(NOW.atOffset(ZoneOffset.UTC));

        // As permissões de quem pediu mudaram: o stream dele é reaberto e já volta com TICKET_HANDLER
        assertThat(published(UserPermissionsChangedEvent.class).userId()).isEqualTo(REQUESTER_ID);
        // E a fila de Aprovações dos admins perdeu um pedido
        DepartmentRequestApprovedEvent approved = published(DepartmentRequestApprovedEvent.class);
        assertThat(approved.eventId()).isNotNull();
        assertThat(approved.requestId()).isEqualTo(REQUEST_ID);
    }

    @Test
    void rejectingKeepsTheRequesterInGeneral() {
        User requester = user(REQUESTER_ID, UserStatus.ACTIVE);
        DepartmentRequest request = request(requester);
        when(departmentRequestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, UserStatus.ACTIVE)));

        DepartmentRequestResponse response = service.reject(REQUEST_ID, ADMIN_ID);

        assertThat(requester.getDepartment()).isSameAs(general);
        assertThat(response.status()).isEqualTo(DepartmentRequestStatus.REJECTED);
        DepartmentRequestRejectedEvent rejected = published(DepartmentRequestRejectedEvent.class);
        assertThat(rejected.eventId()).isNotNull();
        assertThat(rejected.requestId()).isEqualTo(REQUEST_ID);
        // Recusar não muda as permissões de ninguém: só esse evento sai
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void alreadyReviewedRequestIsConflict() {
        DepartmentRequest request = request(user(REQUESTER_ID, UserStatus.ACTIVE));
        ReflectionTestUtils.setField(request, "status", DepartmentRequestStatus.APPROVED);
        when(departmentRequestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.reject(REQUEST_ID, ADMIN_ID))
                .isInstanceOf(DepartmentRequestAlreadyReviewedException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void requestOfInactiveAccountCannotBeApproved() {
        User requester = user(REQUESTER_ID, UserStatus.DEACTIVATED);
        when(departmentRequestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request(requester)));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user(ADMIN_ID, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.approve(REQUEST_ID, ADMIN_ID)).isInstanceOf(InvalidAccountStatusException.class);
        assertThat(requester.getDepartment()).isSameAs(general);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unknownRequestIsNotFound() {
        when(departmentRequestRepository.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(REQUEST_ID, ADMIN_ID))
                .isInstanceOf(DepartmentRequestNotFoundException.class);
    }

    // ---------- auxiliares ----------

    // Os eventos são records (não ApplicationEvent): o publishEvent(Object) é o chamado
    private <T> T published(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new AssertionError("Evento não publicado: " + type.getSimpleName()));
    }

    private DepartmentRequest request(User requester) {
        DepartmentRequest request = new DepartmentRequest(requester, innovation);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        return request;
    }

    private User user(UUID id, UserStatus status) {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash", general);
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
