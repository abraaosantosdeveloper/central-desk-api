package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.MetricsFilter;
import br.com.contabilizape.centraldesk.dto.MetricsResponse;
import br.com.contabilizape.centraldesk.exception.InvalidMetricsPeriodException;
import br.com.contabilizape.centraldesk.exception.MetricsForbiddenException;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.MetricsRepository;
import br.com.contabilizape.centraldesk.repository.MetricsRepository.Scope;
import br.com.contabilizape.centraldesk.repository.MetricsRepository.Window;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regras do painel de métricas que não dependem do banco: escopo por perfil (D4 e D5), período padrão e
 * período anterior, dias sem movimento e janelas de tempo. As consultas em si são cobertas em
 * MetricsIntegrationTest.
 */
class MetricsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Recife");
    // 28/10/2026, 12h no Recife
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZONE);

    private final MetricsRepository metrics = mock(MetricsRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private MetricsService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID financeId = UUID.randomUUID();
    private final UUID fiscalId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        service = new MetricsService(metrics, users, admins, departments, CLOCK, ZONE);
        user = mock(User.class);
        Department finance = department(financeId, "Financeiro");
        when(user.getId()).thenReturn(userId);
        when(user.isActive()).thenReturn(true);
        when(user.getDepartment()).thenReturn(finance);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        Department fiscal = department(fiscalId, "Fiscal");
        when(departments.findById(fiscalId)).thenReturn(Optional.of(fiscal));
        when(metrics.openedPerDay(any(), any())).thenReturn(Map.of());
        when(metrics.closedPerDay(any(), any())).thenReturn(Map.of());
        when(metrics.byPriority(any(), any())).thenReturn(List.of());
        when(metrics.byCategory(any(), any())).thenReturn(List.of());
        when(metrics.byOriginDepartment(any(), any())).thenReturn(List.of());
        when(metrics.byAssignee(any(), any())).thenReturn(List.of());
        when(metrics.oldestUnassigned(any())).thenReturn(List.of());
    }

    // ---------- escopo ----------

    @Test
    void adminSeesEverySectorAndMayFilterByOrigin() {
        when(admins.existsById(userId)).thenReturn(true);

        MetricsResponse all = service.metrics(userId, new MetricsFilter(null, null, null, null, null));
        MetricsResponse fiscal = service.metrics(userId, new MetricsFilter(null, null, fiscalId, null, null));

        assertThat(all.sector()).isNull();
        assertThat(all.sectorLocked()).isFalse();
        assertThat(fiscal.sector().name()).isEqualTo("Fiscal");
        ArgumentCaptor<Scope> scopes = ArgumentCaptor.forClass(Scope.class);
        verify(metrics, org.mockito.Mockito.atLeastOnce()).live(scopes.capture());
        assertThat(scopes.getAllValues()).extracting(Scope::originDepartmentId).containsExactly(null, fiscalId);
    }

    @Test
    void coordinatorIsLockedToOwnSectorAndIgnoresTheOriginFilter() {
        when(users.isCoordinator(userId)).thenReturn(true);

        MetricsResponse response = service.metrics(userId, new MetricsFilter(null, null, fiscalId, null, null));

        assertThat(response.sectorLocked()).isTrue();
        assertThat(response.sector().id()).isEqualTo(financeId);
        assertThat(response.byOriginDepartment()).isEmpty();
        ArgumentCaptor<Scope> scope = ArgumentCaptor.forClass(Scope.class);
        verify(metrics).live(scope.capture());
        assertThat(scope.getValue().originDepartmentId()).isEqualTo(financeId);
        verify(metrics, never()).byOriginDepartment(any(), any());
    }

    @Test
    void otherUsersAreForbidden() {
        assertThatThrownBy(() -> service.metrics(userId, new MetricsFilter(null, null, null, null, null)))
                .isInstanceOf(MetricsForbiddenException.class);
        verify(metrics, never()).summary(any(), any());
    }

    // ---------- período ----------

    @Test
    void withoutDatesTheLastThirtyDaysUpToTodayAreUsed() {
        when(admins.existsById(userId)).thenReturn(true);

        MetricsResponse response = service.metrics(userId, new MetricsFilter(null, null, null, null, null));

        assertThat(response.period().to()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(response.period().from()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(response.perDay()).hasSize(30);
    }

    @Test
    void previousPeriodHasTheSameLengthAndEndsTheDayBefore() {
        when(admins.existsById(userId)).thenReturn(true);

        MetricsResponse response = service.metrics(userId,
                new MetricsFilter(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 21), null, null, null));

        assertThat(response.previousPeriod().from()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(response.previousPeriod().to()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    void windowsRunFromMidnightToMidnightOfTheDayAfterInTheCompanyZone() {
        when(admins.existsById(userId)).thenReturn(true);

        service.metrics(userId, new MetricsFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), null, null, null));

        ArgumentCaptor<Window> windows = ArgumentCaptor.forClass(Window.class);
        verify(metrics, org.mockito.Mockito.times(2)).summary(any(), windows.capture());
        Window current = windows.getAllValues().get(0);
        assertThat(current.from().toString()).isEqualTo("2026-10-01T00:00-03:00");
        assertThat(current.to().toString()).isEqualTo("2026-10-08T00:00-03:00");
    }

    @Test
    void daysWithoutMovementAppearWithZeros() {
        when(admins.existsById(userId)).thenReturn(true);
        LocalDate day2 = LocalDate.of(2026, 10, 2);
        when(metrics.openedPerDay(any(), any())).thenReturn(Map.of(day2, 3L));
        when(metrics.closedPerDay(any(), any())).thenReturn(Map.of(day2, 1L, LocalDate.of(2026, 10, 3), 2L));

        MetricsResponse response = service.metrics(userId,
                new MetricsFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), null, null, null));

        assertThat(response.perDay()).extracting(MetricsResponse.Day::opened).containsExactly(0L, 3L, 0L);
        assertThat(response.perDay()).extracting(MetricsResponse.Day::closed).containsExactly(0L, 1L, 2L);
    }

    @Test
    void invertedPeriodIsRejected() {
        when(admins.existsById(userId)).thenReturn(true);

        assertThatThrownBy(() -> service.metrics(userId,
                new MetricsFilter(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1), null, null, null)))
                .isInstanceOf(InvalidMetricsPeriodException.class);
    }

    @Test
    void periodLongerThanOneYearIsRejected() {
        when(admins.existsById(userId)).thenReturn(true);

        assertThatThrownBy(() -> service.metrics(userId,
                new MetricsFilter(LocalDate.of(2025, 10, 1), LocalDate.of(2026, 10, 28), null, null, null)))
                .isInstanceOf(InvalidMetricsPeriodException.class);
    }

    private static Department department(UUID id, String name) {
        Department department = mock(Department.class);
        when(department.getId()).thenReturn(id);
        when(department.getName()).thenReturn(name);
        return department;
    }
}
