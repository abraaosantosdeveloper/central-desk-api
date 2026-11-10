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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Setor de origem e setor de destino no chamado (SCRUM-213, DECISOES-PAINEIS-METRICAS-BI §2):
 * a origem é o departamento de quem abriu, no momento da abertura, e não acompanha mudanças de setor;
 * só um administrador pode corrigi-la (PATCH /tickets/{id}/origin-department, D2 revisada).
 * O destino, por enquanto, é sempre a Inovação.
 */
@IntegrationTest
class TicketDepartmentsIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String HANDLER = "carlos.inovacao@contabilizape.com.br";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID maria;
    private UUID finance;
    private UUID fiscal;
    private String hardware;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        finance = departmentId("Financeiro");
        fiscal = departmentId("Fiscal");
        maria = register(MARIA, "52998224725");
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", finance, maria);
        hardware = jdbc.queryForObject("SELECT id FROM categories WHERE label = 'Serviços'", String.class);
    }

    // A limpeza padrão preserva administradores; o criado aqui sai ao final
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void openedTicketHasTheRequesterDepartmentAsOriginAndInnovationAsTarget() throws Exception {
        mockMvc.perform(post("/tickets").cookie(login(MARIA)).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originDepartment.id").value(finance.toString()))
                .andExpect(jsonPath("$.originDepartment.name").value("Financeiro"))
                .andExpect(jsonPath("$.targetDepartment.id").value(INNOVATION_ID))
                .andExpect(jsonPath("$.targetDepartment.name").value("TI & Suporte"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT origin_department_id, target_department_id FROM tickets WHERE requester_id = ?", maria);
        assertThat(row.get("origin_department_id")).isEqualTo(finance);
        assertThat(row.get("target_department_id")).isEqualTo(UUID.fromString(INNOVATION_ID));
    }

    @Test
    void originStaysTheSameAfterTheRequesterChangesDepartment() throws Exception {
        UUID ticket = open();

        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", fiscal, maria);
        Cookie session = login(MARIA);

        mockMvc.perform(get("/tickets/" + ticket).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originDepartment.name").value("Financeiro"));
        mockMvc.perform(get("/tickets/mine").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originDepartment.name").value("Financeiro"))
                .andExpect(jsonPath("$.content[0].targetDepartment.name").value("TI & Suporte"));
    }

    @Test
    void originStaysTheSameThroughAssignAndClose() throws Exception {
        UUID ticket = open();
        UUID handler = register(HANDLER, "39053344705");
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, handler);
        Cookie handlerSession = login(HANDLER);

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(handlerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originDepartment.name").value("Financeiro"));
        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(handlerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"resolution\": \"Troquei o toner.\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originDepartment.name").value("Financeiro"))
                .andExpect(jsonPath("$.targetDepartment.name").value("TI & Suporte"));
    }

    @Test
    void queueFilterUsesTheRecordedOriginNotTheCurrentDepartment() throws Exception {
        UUID ticket = open();
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", fiscal, maria);
        UUID handler = register(HANDLER, "39053344705");
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, handler);

        mockMvc.perform(get("/tickets/queue").param("departmentId", finance.toString()).cookie(login(HANDLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ticket.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/tickets/queue").param("departmentId", fiscal.toString()).cookie(login(HANDLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // Inserts feitos fora da aplicação (scripts, testes) recebem a mesma regra pelo banco
    @Test
    void insertWithoutDepartmentsGetsTheRequesterDepartmentAndInnovation() {
        UUID ticket = jdbc.queryForObject("""
                INSERT INTO tickets (title, description, category_id, priority_id, requester_id)
                VALUES ('Por SQL', 'Sem setores', ?::uuid, 2, ?) RETURNING id
                """, UUID.class, hardware, maria);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT origin_department_id, target_department_id FROM tickets WHERE id = ?", ticket);
        assertThat(row.get("origin_department_id")).isEqualTo(finance);
        assertThat(row.get("target_department_id")).isEqualTo(UUID.fromString(INNOVATION_ID));
    }

    // ---------- Correção do setor de origem: só administradores ----------

    @Test
    void adminChangesTheOriginDepartment() throws Exception {
        UUID ticket = open();

        mockMvc.perform(patch("/tickets/" + ticket + "/origin-department").cookie(adminSession())
                        .contentType(MediaType.APPLICATION_JSON).content(originBody(fiscal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originDepartment.id").value(fiscal.toString()))
                .andExpect(jsonPath("$.originDepartment.name").value("Fiscal"))
                .andExpect(jsonPath("$.targetDepartment.name").value("TI & Suporte"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        assertThat(jdbc.queryForObject("SELECT origin_department_id FROM tickets WHERE id = ?", UUID.class, ticket))
                .isEqualTo(fiscal);
    }

    @Test
    void adminCanCorrectTheOriginOfAClosedTicket() throws Exception {
        UUID ticket = open();
        jdbc.update("UPDATE tickets SET status = 'CLOSED' WHERE id = ?", ticket);

        mockMvc.perform(patch("/tickets/" + ticket + "/origin-department").cookie(adminSession())
                        .contentType(MediaType.APPLICATION_JSON).content(originBody(fiscal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originDepartment.name").value("Fiscal"))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void requesterCannotChangeTheOriginDepartment() throws Exception {
        UUID ticket = open();

        mockMvc.perform(patch("/tickets/" + ticket + "/origin-department").cookie(login(MARIA))
                        .contentType(MediaType.APPLICATION_JSON).content(originBody(fiscal)))
                .andExpect(status().isForbidden());
        assertThat(originOf(ticket)).isEqualTo(finance);
    }

    @Test
    void innovationMemberWhoIsNotAdminCannotChangeTheOriginDepartment() throws Exception {
        UUID ticket = open();
        UUID handler = register(HANDLER, "39053344705");
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, handler);

        mockMvc.perform(patch("/tickets/" + ticket + "/origin-department").cookie(login(HANDLER))
                        .contentType(MediaType.APPLICATION_JSON).content(originBody(fiscal)))
                .andExpect(status().isForbidden());
        assertThat(originOf(ticket)).isEqualTo(finance);
    }

    // O token vale até 15 min: quem deixou de ser admin nesse meio-tempo é barrado pelo banco
    @Test
    void formerAdminCannotChangeTheOriginEvenWithTheOldToken() throws Exception {
        UUID ticket = open();
        Cookie session = adminSession();
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);

        mockMvc.perform(patch("/tickets/" + ticket + "/origin-department").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content(originBody(fiscal)))
                .andExpect(status().isForbidden());
        assertThat(originOf(ticket)).isEqualTo(finance);
    }

    @Test
    void sameOriginIsAConflict() throws Exception {
        UUID ticket = open();

        mockMvc.perform(patch("/tickets/" + ticket + "/origin-department").cookie(adminSession())
                        .contentType(MediaType.APPLICATION_JSON).content(originBody(finance)))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownDepartmentIsBadRequest() throws Exception {
        UUID ticket = open();

        mockMvc.perform(patch("/tickets/" + ticket + "/origin-department").cookie(adminSession())
                        .contentType(MediaType.APPLICATION_JSON).content(originBody(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        assertThat(originOf(ticket)).isEqualTo(finance);
    }

    @Test
    void missingDepartmentIdIsBadRequest() throws Exception {
        UUID ticket = open();

        mockMvc.perform(patch("/tickets/" + ticket + "/origin-department").cookie(adminSession())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTicketIsNotFound() throws Exception {
        mockMvc.perform(patch("/tickets/" + UUID.randomUUID() + "/origin-department").cookie(adminSession())
                        .contentType(MediaType.APPLICATION_JSON).content(originBody(fiscal)))
                .andExpect(status().isNotFound());
    }

    // ---------- auxiliares ----------

    // Administradores são somente da Inovação
    private Cookie adminSession() throws Exception {
        UUID admin = register(ADMIN, "11144477735");
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, admin);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        return login(ADMIN);
    }

    private UUID originOf(UUID ticket) {
        return jdbc.queryForObject("SELECT origin_department_id FROM tickets WHERE id = ?", UUID.class, ticket);
    }

    private static String originBody(UUID departmentId) {
        return "{ \"departmentId\": \"%s\" }".formatted(departmentId);
    }

    private UUID open() throws Exception {
        String location = mockMvc.perform(post("/tickets").cookie(login(MARIA))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        return UUID.fromString(location.substring("/tickets/".length()));
    }

    private String body() {
        return """
                { "title": "Impressora parada", "description": "A impressora do 2º andar mostra erro de papel.",
                  "categoryId": "%s", "priorityId": 2 }
                """.formatted(hardware);
    }

    private UUID departmentId(String name) {
        return jdbc.queryForObject("SELECT id FROM departments WHERE name = ?", UUID.class, name);
    }

    private UUID register(String email, String cpf) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Maria da Silva", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(cpf, email, PASSWORD, GENERAL_ID)))
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
