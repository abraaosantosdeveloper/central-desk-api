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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quem comenta (SCRUM-172), reatribuição (SCRUM-173) e filtros/ordenação das listas de chamados (SCRUM-168).
 */
@IntegrationTest
class TicketManagementIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String CARLOS = "carlos.inovacao@contabilizape.com.br";
    private static final String BEATRIZ = "beatriz.inovacao@contabilizape.com.br";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID maria;
    private UUID carlos;
    private UUID beatriz;
    private UUID admin;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        maria = register(MARIA, "52998224725", "Maria da Silva");
        carlos = register(CARLOS, "39053344705", "Carlos Lima");
        beatriz = register(BEATRIZ, "11144477735", "Beatriz Souza");
        admin = register(ADMIN, "71428793860", "Ana Admin");
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id IN (?, ?)", INNOVATION_ID, carlos, beatriz);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // ---------- SCRUM-172: só o solicitante e o responsável comentam ----------

    @Test
    void onlyRequesterAndAssigneeComment() throws Exception {
        UUID ticket = ticket("Impressora", 2, "IN_PROGRESS", carlos);

        comment(ticket, login(MARIA)).andExpect(status().isCreated());
        comment(ticket, login(CARLOS)).andExpect(status().isCreated());
        comment(ticket, login(BEATRIZ)).andExpect(status().isForbidden());
        comment(ticket, login(ADMIN)).andExpect(status().isForbidden());

        // Ver continua liberado para a Inovação e os admins
        mockMvc.perform(get("/tickets/" + ticket + "/comments").cookie(login(BEATRIZ))).andExpect(status().isOk());
        mockMvc.perform(get("/tickets/" + ticket + "/comments").cookie(login(ADMIN))).andExpect(status().isOk());
    }

    @Test
    void ticketInTheQueueOnlyTakesCommentsFromTheRequester() throws Exception {
        UUID ticket = ticket("Impressora", 2, "OPEN", null);

        comment(ticket, login(MARIA)).andExpect(status().isCreated());
        comment(ticket, login(CARLOS)).andExpect(status().isForbidden());
    }

    // ---------- SCRUM-173: reatribuir ----------

    @Test
    void listsPossibleAssigneesWithTheirWorkload() throws Exception {
        ticket("Um", 2, "IN_PROGRESS", carlos);
        ticket("Dois", 2, "IN_PROGRESS", carlos);

        mockMvc.perform(get("/tickets/assignees").cookie(login(CARLOS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].fullName", containsInAnyOrder("Ana Admin", "Beatriz Souza", "Carlos Lima")))
                .andExpect(jsonPath("$[?(@.fullName == 'Carlos Lima')].inProgress", contains(2)))
                .andExpect(jsonPath("$[?(@.fullName == 'Beatriz Souza')].inProgress", contains(0)));

        mockMvc.perform(get("/tickets/assignees").cookie(login(MARIA))).andExpect(status().isForbidden());
    }

    @Test
    void assigneeHandsTheTicketToAColleague() throws Exception {
        UUID ticket = ticket("Impressora", 2, "IN_PROGRESS", carlos);
        Object assignedAt = jdbc.queryForObject("SELECT assigned_at FROM tickets WHERE id = ?", Object.class, ticket);

        reassign(ticket, beatriz, login(CARLOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(beatriz.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        Map<String, Object> row = jdbc.queryForMap("SELECT assignee_id, assigned_at FROM tickets WHERE id = ?", ticket);
        assertThat(row.get("assignee_id")).isEqualTo(beatriz);
        assertThat(row.get("assigned_at")).isEqualTo(assignedAt);
    }

    @Test
    void adminAssignsATicketFromTheQueueToSomeone() throws Exception {
        UUID ticket = ticket("Impressora", 2, "OPEN", null);

        reassign(ticket, carlos, login(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assigneeId").value(carlos.toString()))
                .andExpect(jsonPath("$.assignedAt").exists());
    }

    @Test
    void invalidReassignmentsAreRejected() throws Exception {
        UUID ticket = ticket("Impressora", 2, "IN_PROGRESS", carlos);
        UUID closed = ticket("Antigo", 2, "CLOSED", carlos);
        Cookie carlosSession = login(CARLOS);

        // Quem não é o responsável nem admin
        reassign(ticket, carlos, login(BEATRIZ)).andExpect(status().isForbidden());
        // Destino: colaborador comum, o próprio solicitante, alguém inexistente
        reassign(ticket, maria, carlosSession).andExpect(status().isBadRequest());
        reassign(ticket, UUID.randomUUID(), carlosSession).andExpect(status().isBadRequest());
        // Mesma pessoa e chamado encerrado
        reassign(ticket, carlos, carlosSession).andExpect(status().isConflict());
        reassign(closed, beatriz, carlosSession).andExpect(status().isConflict());
        mockMvc.perform(patch("/tickets/" + ticket + "/reassign").cookie(carlosSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ticketCannotBeReassignedToItsRequester() throws Exception {
        UUID ticket = jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id, assigned_at)
                VALUES ('Do Carlos', 'Aberto pela Inovação', 'IN_PROGRESS', ?, 2, ?, ?, now()) RETURNING id
                """, UUID.class, anyCategory(), beatriz, carlos);

        reassign(ticket, beatriz, login(CARLOS)).andExpect(status().isBadRequest());
    }

    // ---------- SCRUM-207: o admin pode receber o chamado que ele mesmo abriu ----------

    @Test
    void ticketOpenedByAnAdminCanBeHandedBackToHim() throws Exception {
        UUID ticket = jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id, assigned_at)
                VALUES ('Integração com o ERP', 'Demanda interna', 'IN_PROGRESS', ?, 2, ?, ?, now()) RETURNING id
                """, UUID.class, anyCategory(), admin, carlos);

        reassign(ticket, admin, login(CARLOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(admin.toString()));
    }

    @Test
    void adminTakesHisOwnTicketFromTheQueueThroughReassign() throws Exception {
        UUID ticket = jdbc.queryForObject("""
                INSERT INTO tickets (title, description, category_id, priority_id, requester_id)
                VALUES ('Integração com o ERP', 'Demanda interna', ?, 2, ?) RETURNING id
                """, UUID.class, anyCategory(), admin);

        reassign(ticket, admin, login(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assigneeId").value(admin.toString()));
    }

    @Test
    void requesterAndAssigneeBeingTheSameAdminCanStillComment() throws Exception {
        UUID ticket = jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id, assigned_at)
                VALUES ('Integração com o ERP', 'Demanda interna', 'IN_PROGRESS', ?, 2, ?, ?, now()) RETURNING id
                """, UUID.class, anyCategory(), admin, admin);

        comment(ticket, login(ADMIN)).andExpect(status().isCreated());
    }

    // ---------- SCRUM-168: busca, departamento e ordenação ----------

    @Test
    void searchesByTitleOrTicketNumber() throws Exception {
        UUID printer = ticket("Impressora sem toner", 2, "OPEN", null);
        ticket("VPN caiu", 2, "OPEN", null);
        Cookie session = login(MARIA);

        mockMvc.perform(get("/tickets/mine").param("q", "IMPRESSORA").cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(printer.toString())));
        mockMvc.perform(get("/tickets/mine").param("q", "#" + printer.toString().substring(0, 8)).cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(printer.toString())));
    }

    @Test
    void queueFiltersByRequesterDepartment() throws Exception {
        UUID finance = jdbc.queryForObject("SELECT id FROM departments WHERE name = 'Financeiro'", UUID.class);
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", finance, maria);
        UUID fromFinance = ticket("Planilha travou", 2, "OPEN", null);

        mockMvc.perform(get("/tickets/queue").param("departmentId", finance.toString()).cookie(login(CARLOS)))
                .andExpect(jsonPath("$.content[*].id", contains(fromFinance.toString())));
        mockMvc.perform(get("/tickets/queue").param("departmentId", GENERAL_ID).cookie(login(CARLOS)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(fromFinance.toString()))));
    }

    @Test
    void ordersByPriorityThenOldestFirst() throws Exception {
        UUID oldLow = ticket("Baixa antiga", 1, "OPEN", null);
        UUID oldCritical = ticket("Crítica antiga", 4, "OPEN", null);
        UUID newCritical = ticket("Crítica nova", 4, "OPEN", null);
        jdbc.update("UPDATE tickets SET created_at = now() - interval '2 hours' WHERE id IN (?, ?)", oldLow, oldCritical);
        Cookie session = login(CARLOS);

        mockMvc.perform(get("/tickets/queue").param("orderBy", "PRIORITY").cookie(session))
                .andExpect(jsonPath("$.content[*].id",
                        contains(oldCritical.toString(), newCritical.toString(), oldLow.toString())));
        mockMvc.perform(get("/tickets/queue").param("orderBy", "RECENT").cookie(session))
                .andExpect(jsonPath("$.content[0].id").value(newCritical.toString()));
    }

    // ---------- auxiliares ----------

    private org.springframework.test.web.servlet.ResultActions comment(UUID ticket, Cookie session) throws Exception {
        return mockMvc.perform(post("/tickets/" + ticket + "/comments").cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"content\": \"Alguma novidade?\" }"));
    }

    private org.springframework.test.web.servlet.ResultActions reassign(UUID ticket, UUID assignee, Cookie session)
            throws Exception {
        return mockMvc.perform(patch("/tickets/" + ticket + "/reassign").cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"assigneeId\": \"%s\" }".formatted(assignee)));
    }

    private UUID anyCategory() {
        return jdbc.queryForObject("SELECT id FROM categories WHERE archived_at IS NULL LIMIT 1", UUID.class);
    }

    private UUID ticket(String title, int priority, String status, UUID assignee) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO tickets (title, description, category_id, priority_id, requester_id)
                VALUES (?, 'Descrição', ?, ?, ?) RETURNING id
                """, UUID.class, title, anyCategory(), priority, maria);
        if (assignee != null) {
            jdbc.update("UPDATE tickets SET status = 'IN_PROGRESS', assignee_id = ?, assigned_at = now() WHERE id = ?",
                    assignee, id);
        }
        if (status.equals("CLOSED")) {
            jdbc.update("UPDATE tickets SET status = 'CLOSED', closed_by_id = ?, closed_at = now() WHERE id = ?", maria, id);
        }
        return id;
    }

    private UUID register(String email, String cpf, String name) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "%s", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(name, cpf, email, PASSWORD, GENERAL_ID)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
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
}
