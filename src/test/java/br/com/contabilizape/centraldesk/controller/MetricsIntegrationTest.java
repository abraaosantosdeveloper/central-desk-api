package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Painel de métricas (GET /dashboard/metrics, SCRUM-216): administrador vê todos os setores com filtros, coordenador
 * vê só o próprio setor como origem (D4), os demais recebem 403, e o período anterior vem junto.
 *
 * Cenário (período padrão = últimos 30 dias): Financeiro abriu "Impressora" (aberta, há 2 dias) e "Antigo"
 * (encerrado, há 45 dias, portanto no período anterior); Fiscal abriu "VPN caiu" (em andamento, há 3 dias,
 * assumida 1 h depois) e "Nota fiscal" (encerrada, há 4 dias, assumida 2 h depois e encerrada 10 h depois,
 * com uma reabertura há 2 dias); a Inovação abriu "Servidor lento" (aberto, há 1 dia). Carlos atende.
 * Paula coordena o Financeiro.
 */
@IntegrationTest
class MetricsIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Recife");
    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final UUID INNOVATION = UUID.fromString("e9790f1f-41df-4369-bfe7-1be4be535561");
    private static final String PASSWORD = "senhaForte123";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String PAULA = "paula.rocha@contabilizape.com.br";
    private static final String JOAO = "joao.souza@contabilizape.com.br";
    private static final String CARLOS = "carlos.inovacao@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID finance;
    private UUID fiscal;
    private UUID hardware;
    private UUID software;
    private UUID printer;
    private UUID vpn;
    private UUID invoice;
    private UUID server;
    private OffsetDateTime invoiceCreatedAt;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        finance = departmentId("Financeiro");
        fiscal = departmentId("Fiscal");
        hardware = jdbc.queryForObject("SELECT id FROM categories WHERE label = 'Hardware'", UUID.class);
        software = jdbc.queryForObject("SELECT id FROM categories WHERE label <> 'Hardware' ORDER BY label LIMIT 1", UUID.class);

        UUID admin = account(ADMIN, "71428793860", "Ana Admin", INNOVATION);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        UUID maria = account(MARIA, "52998224725", "Maria da Silva", finance);
        UUID paula = account(PAULA, "11144477735", "Paula Rocha", finance);
        jdbc.update("INSERT INTO coordinators (user_id, granted_by_id) VALUES (?, ?)", paula, admin);
        UUID joao = account(JOAO, "86288366757", "João Souza", fiscal);
        UUID carlos = account(CARLOS, "39053344705", "Carlos Lima", INNOVATION);

        printer = ticket("Impressora", maria, finance, "OPEN", null, hardware, 2, 2, null, null);
        vpn = ticket("VPN caiu", joao, fiscal, "IN_PROGRESS", carlos, hardware, 4, 3, 60, null);
        invoice = ticket("Nota fiscal", joao, fiscal, "CLOSED", carlos, software, 1, 4, 120, 600);
        server = ticket("Servidor lento", carlos, INNOVATION, "OPEN", null, software, 3, 1, null, null);
        ticket("Antigo", maria, finance, "CLOSED", carlos, software, 2, 45, 30, 60);

        invoiceCreatedAt = jdbc.queryForObject("SELECT created_at FROM tickets WHERE id = ?", OffsetDateTime.class, invoice);
        jdbc.update("""
                INSERT INTO ticket_closures (ticket_id, closed_by_id, closed_at, reopened_by_id, reopened_at, reopen_reason)
                VALUES (?, ?, now() - interval '3 days', ?, now() - interval '2 days', 'Voltou a falhar')
                """, invoice, carlos, joao);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // ---------- administrador ----------

    @Test
    void adminSeesTheWholeCompanyInTheDefaultPeriod() throws Exception {
        LocalDate today = LocalDate.now(ZONE);

        mockMvc.perform(get("/dashboard/metrics").cookie(login(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.to").value(today.toString()))
                .andExpect(jsonPath("$.period.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.previousPeriod.to").value(today.minusDays(30).toString()))
                .andExpect(jsonPath("$.sector").value(nullValue()))
                .andExpect(jsonPath("$.sectorLocked").value(false))
                .andExpect(jsonPath("$.summary.total").value(4))
                .andExpect(jsonPath("$.summary.taken").value(2))
                .andExpect(jsonPath("$.summary.closed").value(1))
                .andExpect(jsonPath("$.summary.reopened").value(1))
                .andExpect(jsonPath("$.summary.avgResponseSeconds").value(5400))
                .andExpect(jsonPath("$.summary.avgResolutionSeconds").value(36000))
                .andExpect(jsonPath("$.perDay", hasSize(30)));
    }

    @Test
    void theSummaryOfThePreviousPeriodComesAlong() throws Exception {
        mockMvc.perform(get("/dashboard/metrics").cookie(login(ADMIN)))
                .andExpect(jsonPath("$.previousSummary.total").value(1))
                .andExpect(jsonPath("$.previousSummary.taken").value(1))
                .andExpect(jsonPath("$.previousSummary.closed").value(1))
                .andExpect(jsonPath("$.previousSummary.avgResponseSeconds").value(1800))
                .andExpect(jsonPath("$.previousSummary.avgResolutionSeconds").value(3600));
    }

    @Test
    void liveCountersIgnoreThePeriod() throws Exception {
        mockMvc.perform(get("/dashboard/metrics").param("from", LocalDate.now(ZONE).toString()).cookie(login(ADMIN)))
                .andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.now.open").value(3))
                .andExpect(jsonPath("$.now.unassigned").value(2));
    }

    @Test
    void groupsTicketsByPriorityCategoryOriginAndAssignee() throws Exception {
        mockMvc.perform(get("/dashboard/metrics").cookie(login(ADMIN)))
                .andExpect(jsonPath("$.byPriority", hasSize(4)))
                .andExpect(jsonPath("$.byPriority[*].count", contains(1, 1, 1, 1)))
                .andExpect(jsonPath("$.byCategory", hasSize(2)))
                .andExpect(jsonPath("$.byCategory[*].count", contains(2, 2)))
                .andExpect(jsonPath("$.byOriginDepartment", hasSize(3)))
                .andExpect(jsonPath("$.byOriginDepartment[0].label").value("Fiscal"))
                .andExpect(jsonPath("$.byOriginDepartment[0].count").value(2))
                .andExpect(jsonPath("$.byAssignee", hasSize(1)))
                .andExpect(jsonPath("$.byAssignee[0].name").value("Carlos Lima"))
                .andExpect(jsonPath("$.byAssignee[0].inProgress").value(1))
                .andExpect(jsonPath("$.byAssignee[0].taken").value(2))
                .andExpect(jsonPath("$.byAssignee[0].closed").value(1))
                .andExpect(jsonPath("$.byAssignee[0].avgResponseSeconds").value(5400))
                .andExpect(jsonPath("$.byAssignee[0].avgResolutionSeconds").value(36000));
    }

    @Test
    void perDayCountsOpenedTicketsOnTheDayTheyWereOpened() throws Exception {
        LocalDate from = LocalDate.now(ZONE).minusDays(29);
        int index = (int) ChronoUnit.DAYS.between(from, invoiceCreatedAt.atZoneSameInstant(ZONE).toLocalDate());

        mockMvc.perform(get("/dashboard/metrics").cookie(login(ADMIN)))
                .andExpect(jsonPath("$.perDay[" + index + "].opened").value(1));
    }

    @Test
    void oldestUnassignedTicketsComeOldestFirst() throws Exception {
        mockMvc.perform(get("/dashboard/metrics").cookie(login(ADMIN)))
                .andExpect(jsonPath("$.oldestUnassigned[*].id", contains(printer.toString(), server.toString())))
                .andExpect(jsonPath("$.oldestUnassigned[0].requesterDepartment").value("Financeiro"));
    }

    @Test
    void adminFiltersByOriginCategoryAndPriority() throws Exception {
        Cookie session = login(ADMIN);

        mockMvc.perform(get("/dashboard/metrics").param("originDepartmentId", fiscal.toString()).cookie(session))
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.sector.name").value("Fiscal"))
                .andExpect(jsonPath("$.now.open").value(1));
        mockMvc.perform(get("/dashboard/metrics").param("categoryId", hardware.toString()).cookie(session))
                .andExpect(jsonPath("$.summary.total").value(2));
        mockMvc.perform(get("/dashboard/metrics").param("priorityId", "4").cookie(session))
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.byPriority[*].count", containsInAnyOrder(0, 0, 0, 1)));
    }

    @Test
    void customPeriodUsesTheGivenDays() throws Exception {
        LocalDate today = LocalDate.now(ZONE);

        mockMvc.perform(get("/dashboard/metrics").param("from", today.minusDays(2).toString())
                        .param("to", today.toString()).cookie(login(ADMIN)))
                .andExpect(jsonPath("$.perDay", hasSize(3)))
                .andExpect(jsonPath("$.summary.total").value(2))
                .andExpect(jsonPath("$.previousPeriod.to").value(today.minusDays(3).toString()));
    }

    @Test
    void invertedPeriodIsABadRequest() throws Exception {
        mockMvc.perform(get("/dashboard/metrics").param("from", "2026-10-10").param("to", "2026-10-01")
                        .cookie(login(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.from").exists());
    }

    // ---------- coordenador (D4) ----------

    @Test
    void coordinatorSeesOnlyOwnSectorAsOrigin() throws Exception {
        mockMvc.perform(get("/dashboard/metrics").cookie(login(PAULA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectorLocked").value(true))
                .andExpect(jsonPath("$.sector.name").value("Financeiro"))
                .andExpect(jsonPath("$.summary.total").value(1))
                .andExpect(jsonPath("$.previousSummary.total").value(1))
                .andExpect(jsonPath("$.now.open").value(1))
                .andExpect(jsonPath("$.byOriginDepartment", hasSize(0)))
                .andExpect(jsonPath("$.oldestUnassigned[*].id", contains(printer.toString())));
    }

    @Test
    void coordinatorCannotWidenTheScopeWithTheOriginFilter() throws Exception {
        mockMvc.perform(get("/dashboard/metrics").param("originDepartmentId", fiscal.toString()).cookie(login(PAULA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector.name").value("Financeiro"))
                .andExpect(jsonPath("$.summary.total").value(1));
    }

    // ---------- demais ----------

    @Test
    void regularUsersAreForbidden() throws Exception {
        mockMvc.perform(get("/dashboard/metrics").cookie(login(MARIA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestsWithoutSessionAreUnauthorized() throws Exception {
        mockMvc.perform(get("/dashboard/metrics"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- apoio ----------

    private UUID departmentId(String name) {
        return jdbc.queryForObject("SELECT id FROM departments WHERE name = ?", UUID.class, name);
    }

    private UUID account(String email, String cpf, String name, UUID department) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "%s", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(name, cpf, email, PASSWORD, GENERAL_ID)))
                .andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", department, id);
        return id;
    }

    private Cookie login(String email) throws Exception {
        String header = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("access_token=")).findFirst().orElseThrow();
        return new Cookie("access_token", header.substring(header.indexOf('=') + 1, header.indexOf(';')));
    }

    /**
     * created_at controlado (dias atrás); as datas de assumir e encerrar são contadas em minutos a partir dele.
     * Chamados sem responsável não têm first_assigned_at; "Antigo" tem os dois (já foi atendido e encerrado).
     */
    private UUID ticket(String title, UUID requester, UUID origin, String status, UUID assignee, UUID category,
                        int priority, int daysAgo, Integer assignedAfterMinutes, Integer closedAfterMinutes) {
        OffsetDateTime created = OffsetDateTime.now().minusDays(daysAgo);
        OffsetDateTime assigned = assignedAfterMinutes == null ? null : created.plusMinutes(assignedAfterMinutes);
        OffsetDateTime closed = closedAfterMinutes == null ? null : created.plusMinutes(closedAfterMinutes);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id,
                                     origin_department_id, target_department_id, assigned_at, first_assigned_at,
                                     closed_at, created_at, updated_at)
                VALUES (?, 'Descrição', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, title, status, category, priority, requester, assignee, origin, INNOVATION,
                assigned, assigned, closed, created, created);
    }
}
