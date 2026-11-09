package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.MetricsFilter;
import br.com.contabilizape.centraldesk.dto.MetricsResponse;
import br.com.contabilizape.centraldesk.dto.MetricsResponse.Day;
import br.com.contabilizape.centraldesk.dto.MetricsResponse.Period;
import br.com.contabilizape.centraldesk.dto.MetricsResponse.Sector;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.DepartmentNotFoundException;
import br.com.contabilizape.centraldesk.exception.InvalidMetricsPeriodException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.MetricsForbiddenException;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.MetricsRepository;
import br.com.contabilizape.centraldesk.repository.MetricsRepository.Scope;
import br.com.contabilizape.centraldesk.repository.MetricsRepository.Window;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Painel de métricas (DECISOES-PAINEIS-METRICAS-BI §1, §4 e §6): administrador vê todos os setores, com o filtro
 * opcional de setor de origem (D5); coordenador vê só o próprio setor, como origem (D4); os demais recebem 403.
 * Quem é administrador ou coordenador, e o setor do coordenador, vêm do banco, nunca do token nem da requisição:
 * o cliente não amplia o próprio escopo.
 *
 * Sem período, valem os últimos 30 dias até hoje. O período anterior tem a mesma duração e termina na véspera.
 */
@Service
public class MetricsService {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 366;

    private final MetricsRepository metrics;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final DepartmentRepository departmentRepository;
    private final Clock clock;
    private final ZoneId zone;

    public MetricsService(MetricsRepository metrics, UserRepository userRepository, AdminRepository adminRepository,
                          DepartmentRepository departmentRepository, Clock clock,
                          @Value("${app.tickets.time-zone}") ZoneId zone) {
        this.metrics = metrics;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.departmentRepository = departmentRepository;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional(readOnly = true)
    public MetricsResponse metrics(UUID userId, MetricsFilter filter) {
        User user = findActiveUser(userId);
        boolean admin = adminRepository.existsById(user.getId());
        if (!admin && !userRepository.isCoordinator(user.getId())) {
            throw new MetricsForbiddenException();
        }

        UUID originId = admin ? filter.originDepartmentId() : user.getDepartment().getId();
        Sector sector = originId == null ? null : sectorOf(originId, user, admin);
        Scope scope = new Scope(originId, filter.categoryId(), filter.priorityId());

        Period period = periodOf(filter);
        Period previous = previousOf(period);
        Window window = windowOf(period);

        return new MetricsResponse(
                period, previous, sector, !admin,
                metrics.summary(scope, window),
                metrics.summary(scope, windowOf(previous)),
                metrics.live(scope),
                perDay(period, metrics.openedPerDay(scope, window), metrics.closedPerDay(scope, window)),
                metrics.byPriority(scope, window),
                metrics.byCategory(scope, window),
                admin ? metrics.byOriginDepartment(scope, window) : List.of(),
                metrics.byAssignee(scope, window),
                metrics.oldestUnassigned(scope),
                clock.instant().atZone(zone).toOffsetDateTime());
    }

    private Period periodOf(MetricsFilter filter) {
        LocalDate to = filter.to() != null ? filter.to() : LocalDate.now(clock.withZone(zone));
        LocalDate from = filter.from() != null ? filter.from() : to.minusDays(DEFAULT_DAYS - 1L);
        if (from.isAfter(to)) {
            throw new InvalidMetricsPeriodException("A data inicial não pode ser depois da data final.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidMetricsPeriodException("O período pode ter no máximo " + MAX_DAYS + " dias.");
        }
        return new Period(from, to);
    }

    // Mesma duração, terminando na véspera do início
    private static Period previousOf(Period period) {
        long days = ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
        LocalDate to = period.from().minusDays(1);
        return new Period(to.minusDays(days - 1), to);
    }

    // [primeiro dia à meia-noite, dia seguinte ao último à meia-noite), no fuso da empresa
    private Window windowOf(Period period) {
        return new Window(period.from().atStartOfDay(zone).toOffsetDateTime(),
                period.to().plusDays(1).atStartOfDay(zone).toOffsetDateTime());
    }

    private static List<Day> perDay(Period period, Map<LocalDate, Long> opened, Map<LocalDate, Long> closed) {
        List<Day> days = new ArrayList<>();
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            days.add(new Day(date, opened.getOrDefault(date, 0L), closed.getOrDefault(date, 0L)));
        }
        return days;
    }

    private Sector sectorOf(UUID departmentId, User user, boolean admin) {
        Department department = admin
                ? departmentRepository.findById(departmentId).orElseThrow(() -> new DepartmentNotFoundException(departmentId))
                : user.getDepartment();
        return new Sector(department.getId(), department.getName());
    }

    // O token vale até 15 min: a conta pode ter sido desativada nesse meio-tempo
    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (!user.isActive()) {
            throw new AccountNotActiveException();
        }
        return user;
    }
}
