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
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Todos os chamados" (GET /tickets/overview, PLANO-REATRIBUICAO-SETOR-E-TODOS-CHAMADOS §2): administrador vê
 * tudo (TC1), coordenador vê o que o próprio setor abriu ou recebeu (TC2), todos os status juntos com contadores
 * por aba (TC3) e os demais recebem 403 (TC4). Também cobre a leitura do chamado pelo coordenador.
 *
 * Cenário: Financeiro abriu "Impressora" para a Inovação; Fiscal abriu "VPN" para a Inovação e "Nota fiscal"
 * para o Financeiro; a Inovação abriu "Servidor" para ela mesma. Paula coordena o Financeiro.
 */
@IntegrationTest
class TicketOverviewIntegrationTest {

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
    private UUID maria;
    private UUID joao;
    private UUID carlos;
    private UUID printer;
    private UUID vpn;
    private UUID invoice;
    private UUID server;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        finance = departmentId("Financeiro");
        fiscal = departmentId("Fiscal");
        hardware = jdbc.queryForObject("SELECT id FROM categories WHERE label = 'Hardware'", UUID.class);
        software = jdbc.queryForObject("SELECT id FROM categories WHERE label <> 'Hardware' ORDER BY label LIMIT 1", UUID.class);

        UUID admin = account(ADMIN, "71428793860", "Ana Admin", INNOVATION);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        maria = account(MARIA, "52998224725", "Maria da Silva", finance);
        UUID paula = account(PAULA, "11144477735", "Paula Rocha", finance);
        jdbc.update("INSERT INTO coordinators (user_id, granted_by_id) VALUES (?, ?)", paula, admin);
        joao = account(JOAO, "86288366757", "João Souza", fiscal);
        carlos = account(CARLOS, "39053344705", "Carlos Lima", INNOVATION);

        printer = ticket("Impressora", maria, finance, INNOVATION, "OPEN", null, hardware, 2, 4);
        vpn = ticket("VPN caiu", joao, fiscal, INNOVATION, "IN_PROGRESS", carlos, hardware, 4, 3);
        invoice = ticket("Nota fiscal", joao, fiscal, finance, "CLOSED", carlos, software, 1, 2);
        server = ticket("Servidor lento", carlos, INNOVATION, INNOVATION, "OPEN", null, software, 3, 1);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // ---------- TC1 e TC3: administrador ----------

    @Test
    void adminSeesEveryTicketOfEveryStatusNewestFirst() throws Exception {
        mockMvc.perform(get("/tickets/overview").cookie(login(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(ids(server, invoice, vpn, printer))))
                .andExpect(jsonPath("$.content[0].originDepartment.name").value("Inovação"))
                .andExpect(jsonPath("$.content[1].targetDepartment.name").value("Financeiro"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.statusCounts.all").value(4))
                .andExpect(jsonPath("$.statusCounts.OPEN").value(2))
                .andExpect(jsonPath("$.statusCounts.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.statusCounts.CLOSED").value(1));
    }

    @Test
    void adminFiltersByOriginAndTarget() throws Exception {
        Cookie session = login(ADMIN);

        mockMvc.perform(get("/tickets/overview").param("originDepartmentId", fiscal.toString()).cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(invoice, vpn))));
        mockMvc.perform(get("/tickets/overview").param("targetDepartmentId", finance.toString()).cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(invoice))));
        mockMvc.perform(get("/tickets/overview").param("originDepartmentId", fiscal.toString())
                        .param("targetDepartmentId", INNOVATION.toString()).cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(vpn))));
    }

    // Os contadores das abas usam os mesmos filtros, menos o de status
    @Test
    void statusCountsFollowTheFiltersExceptStatus() throws Exception {
        mockMvc.perform(get("/tickets/overview").param("status", "OPEN").param("categoryId", hardware.toString())
                        .cookie(login(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(ids(printer))))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.statusCounts.all").value(2))
                .andExpect(jsonPath("$.statusCounts.OPEN").value(1))
                .andExpect(jsonPath("$.statusCounts.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.statusCounts.CLOSED").value(0));
    }

    @Test
    void searchesByTitleNumberAndRequesterName() throws Exception {
        Cookie session = login(ADMIN);
        Long number = jdbc.queryForObject("SELECT ticket_number FROM tickets WHERE id = ?", Long.class, vpn);

        mockMvc.perform(get("/tickets/overview").param("q", "impressora").cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(printer))));
        mockMvc.perform(get("/tickets/overview").param("q", "joão").cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(invoice, vpn))));
        mockMvc.perform(get("/tickets/overview").param("q", "#" + number).cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(vpn))));
    }

    @Test
    void filtersByOpeningDayAndOrdersByPriority() throws Exception {
        Cookie session = login(ADMIN);
        // Os outros foram abertos há poucas horas: no máximo ontem, no fuso da empresa
        jdbc.update("UPDATE tickets SET created_at = now() - interval '10 days' WHERE id = ?", printer);
        LocalDate today = LocalDate.now(ZoneId.of("America/Recife"));

        mockMvc.perform(get("/tickets/overview").param("to", today.minusDays(5).toString()).cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(printer))));
        mockMvc.perform(get("/tickets/overview").param("from", today.minusDays(2).toString())
                        .param("to", today.toString()).param("orderBy", "PRIORITY").cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(vpn, server, invoice))));
    }

    @Test
    void isPaginated() throws Exception {
        mockMvc.perform(get("/tickets/overview").param("page", "1").param("size", "3").cookie(login(ADMIN)))
                .andExpect(jsonPath("$.content[*].id", contains(ids(printer))))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.statusCounts.all").value(4));
    }

    // ---------- TC2: coordenador ----------

    @Test
    void coordinatorSeesWhatTheSectorOpenedOrReceived() throws Exception {
        mockMvc.perform(get("/tickets/overview").cookie(login(PAULA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(ids(invoice, printer))))
                .andExpect(jsonPath("$.statusCounts.all").value(2))
                .andExpect(jsonPath("$.statusCounts.OPEN").value(1))
                .andExpect(jsonPath("$.statusCounts.CLOSED").value(1));
    }

    @Test
    void relationNarrowsToOriginOrTarget() throws Exception {
        Cookie session = login(PAULA);

        mockMvc.perform(get("/tickets/overview").param("relation", "ORIGIN").cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(printer))));
        mockMvc.perform(get("/tickets/overview").param("relation", "TARGET").cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ids(invoice))));
        mockMvc.perform(get("/tickets/overview").param("relation", "BOTH").cookie(session))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(ids(invoice, printer))));
    }

    @Test
    void coordinatorCannotWidenTheScope() throws Exception {
        mockMvc.perform(get("/tickets/overview").param("originDepartmentId", fiscal.toString())
                        .param("targetDepartmentId", INNOVATION.toString()).cookie(login(PAULA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(ids(invoice, printer))));
    }

    // O setor do coordenador vem do banco: quem muda de setor perde a coordenação e o acesso
    @Test
    void coordinatorScopeIsReadFromTheDatabase() throws Exception {
        Cookie session = login(PAULA);
        jdbc.update("DELETE FROM coordinators");

        mockMvc.perform(get("/tickets/overview").cookie(session)).andExpect(status().isForbidden());
    }

    // ---------- TC4 ----------

    @Test
    void collaboratorAndInnovationMemberWithoutCoordinationAreForbidden() throws Exception {
        mockMvc.perform(get("/tickets/overview").cookie(login(MARIA))).andExpect(status().isForbidden());
        mockMvc.perform(get("/tickets/overview").cookie(login(CARLOS))).andExpect(status().isForbidden());
        mockMvc.perform(get("/tickets/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidFiltersAreBadRequest() throws Exception {
        Cookie session = login(ADMIN);
        mockMvc.perform(get("/tickets/overview").param("relation", "VIZINHO").cookie(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/tickets/overview").param("from", "28/10/2026").cookie(session))
                .andExpect(status().isBadRequest());
    }

    // ---------- leitura do chamado pelo coordenador ----------

    @Test
    void coordinatorReadsTicketsOfTheSectorButNotOthers() throws Exception {
        Cookie session = login(PAULA);

        mockMvc.perform(get("/tickets/" + printer).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Impressora"));
        mockMvc.perform(get("/tickets/" + invoice).cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get("/tickets/" + vpn).cookie(session)).andExpect(status().isNotFound());
    }

    @Test
    void coordinatorReadsTheConversationButDoesNotWrite() throws Exception {
        mockMvc.perform(post("/tickets/" + printer + "/comments").cookie(login(MARIA))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"content\": \"Continua sem imprimir.\" }"))
                .andExpect(status().isCreated());
        Cookie session = login(PAULA);

        mockMvc.perform(get("/tickets/" + printer + "/comments").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Continua sem imprimir."));
        mockMvc.perform(post("/tickets/" + printer + "/comments").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"content\": \"Estou acompanhando.\" }"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/tickets/" + vpn + "/comments").cookie(session)).andExpect(status().isNotFound());
    }

    // ---------- auxiliares ----------

    private static String[] ids(UUID... tickets) {
        String[] ids = new String[tickets.length];
        for (int i = 0; i < tickets.length; i++) {
            ids[i] = tickets[i].toString();
        }
        return ids;
    }

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

    // created_at controlado (horas atrás) para a ordem não depender da velocidade do teste
    private UUID ticket(String title, UUID requester, UUID origin, UUID target, String status, UUID assignee,
                        UUID category, int priority, int hoursAgo) {
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id,
                                     origin_department_id, target_department_id, created_at, updated_at)
                VALUES (?, 'Descrição', ?, ?, ?, ?, ?, ?, ?, now() - make_interval(hours => ?), now() - make_interval(hours => ?))
                RETURNING id
                """, UUID.class, title, status, category, priority, requester, assignee, origin, target, hoursAgo, hoursAgo);
    }
}
