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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Painel do administrador (SCRUM-165): contas (listar, consultar, desativar, reativar)
 * e pedidos de entrada na Inovação (listar, aprovar, recusar).
 */
@IntegrationTest
class AdminAccountsIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String JOAO = "joao.souza@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID admin;
    private Cookie adminSession;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        admin = register(ADMIN, "71428793860", "Ana Admin", GENERAL_ID);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        adminSession = accessCookie(login(ADMIN));
    }

    // A limpeza padrão preserva administradores; os criados aqui saem ao final
    @AfterEach
    void removeTestAdmins() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@contabilizape.com.br')");
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // ---------- listar e consultar ----------

    @Test
    void listsAccountsByNameWithFiltersAndWithoutAnonymized() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        UUID joao = register(JOAO, "11144477735", "João Souza", GENERAL_ID);
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE id = ?", joao);
        UUID removed = register("ex.colaborador@contabilizape.com.br", "39053344705", "Bruno", GENERAL_ID);
        jdbc.update("UPDATE users SET status = 'ANONYMIZED', full_name = 'Usuário removido' WHERE id = ?", removed);

        mockMvc.perform(get("/users").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].fullName", contains("Ana Admin", "João Souza", "Maria da Silva")))
                .andExpect(jsonPath("$.content[0].admin").value(true))
                .andExpect(jsonPath("$.content[0].cpf").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/users").param("status", "DEACTIVATED").cookie(adminSession))
                .andExpect(jsonPath("$.content[*].fullName", contains("João Souza")));
        mockMvc.perform(get("/users").param("search", "MARIA.SILVA").cookie(adminSession))
                .andExpect(jsonPath("$.content[*].id", contains(maria.toString())));
        mockMvc.perform(get("/users").param("status", "ANONYMIZED").cookie(adminSession))
                .andExpect(jsonPath("$.content[*].fullName", contains("Usuário removido")));
    }

    @Test
    void showsOneAccount() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", INNOVATION_ID);

        mockMvc.perform(get("/users/" + maria).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(MARIA))
                .andExpect(jsonPath("$.departmentName").value("Geral"))
                .andExpect(jsonPath("$.departmentApprovalPending").value(true))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.ticketHandler").value(false));

        mockMvc.perform(get("/users/" + UUID.randomUUID()).cookie(adminSession)).andExpect(status().isNotFound());
    }

    // ---------- desativar e reativar ----------

    @Test
    void deactivationBlocksTheAccountImmediately() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        MockHttpServletResponse mariaLogin = login(MARIA);

        mockMvc.perform(patch("/users/" + maria + "/deactivate").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"))
                .andExpect(jsonPath("$.deactivatedAt").exists());

        // O access token ainda não venceu, mas a API já recusa; a sessão não renova mais
        mockMvc.perform(get("/users/me").cookie(accessCookie(mariaLogin))).andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", cookie(mariaLogin, "refresh_token"))))
                .andExpect(status().isUnauthorized());
        loginExpecting(MARIA, 403);
    }

    @Test
    void deactivationReturnsTicketsInProgressToTheQueue() throws Exception {
        UUID handler = register(JOAO, "11144477735", "João Souza", GENERAL_ID);
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", UUID.fromString(INNOVATION_ID), handler);
        UUID requester = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        UUID ticket = ticket(requester, handler);

        mockMvc.perform(patch("/users/" + handler + "/deactivate").cookie(adminSession)).andExpect(status().isOk());

        assertThat(jdbc.queryForMap("SELECT status, assignee_id FROM tickets WHERE id = ?", ticket))
                .containsEntry("status", "OPEN").containsEntry("assignee_id", null);
    }

    @Test
    void reactivatedAccountCanLogInAgain() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        mockMvc.perform(patch("/users/" + maria + "/deactivate").cookie(adminSession)).andExpect(status().isOk());

        mockMvc.perform(patch("/users/" + maria + "/reactivate").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.deactivatedAt").doesNotExist());
        loginExpecting(MARIA, 200);
    }

    @Test
    void invalidStatusChangesAreConflicts() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);

        mockMvc.perform(patch("/users/" + maria + "/reactivate").cookie(adminSession))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/" + admin + "/deactivate").cookie(adminSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Você não pode desativar a sua própria conta."));
    }

    // ---------- pedidos de entrada na Inovação ----------

    @Test
    void approvedRequesterBecomesTicketHandlerOnTheNextRenewal() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", INNOVATION_ID);
        MockHttpServletResponse mariaLogin = login(MARIA);
        String request = jdbc.queryForObject("SELECT id FROM department_requests WHERE user_id = ?", String.class, maria);

        mockMvc.perform(get("/department-requests").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(request)))
                .andExpect(jsonPath("$[0].userName").value("Maria da Silva"))
                .andExpect(jsonPath("$[0].departmentName").value("Inovação"));

        mockMvc.perform(patch("/department-requests/" + request + "/approve").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedById").value(admin.toString()));

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", cookie(mariaLogin, "refresh_token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Inovação"))
                .andExpect(jsonPath("$.departmentApprovalPending").value(false))
                .andExpect(jsonPath("$.roles", hasItem("TICKET_HANDLER")));
        mockMvc.perform(get("/department-requests").cookie(adminSession))
                .andExpect(jsonPath("$[*].id", not(hasItem(request))));
    }

    @Test
    void rejectedRequesterStaysInGeneralAndCannotBeReviewedTwice() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", INNOVATION_ID);
        String request = jdbc.queryForObject("SELECT id FROM department_requests WHERE user_id = ?", String.class, maria);

        mockMvc.perform(patch("/department-requests/" + request + "/reject").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(jdbc.queryForObject("SELECT department_id FROM users WHERE id = ?", String.class, maria))
                .isEqualTo(GENERAL_ID);

        mockMvc.perform(patch("/department-requests/" + request + "/approve").cookie(adminSession))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/department-requests/" + UUID.randomUUID() + "/approve").cookie(adminSession))
                .andExpect(status().isNotFound());
    }

    // ---------- permissões ----------

    @Test
    void collaboratorCannotUseTheAdminPanelButKeepsHisOwnRoutes() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        Cookie mariaSession = accessCookie(login(MARIA));

        mockMvc.perform(get("/users").cookie(mariaSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/users/" + admin).cookie(mariaSession)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/users/" + admin + "/deactivate").cookie(mariaSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/department-requests").cookie(mariaSession)).andExpect(status().isForbidden());

        // /users/me continua aberto para qualquer usuário logado
        mockMvc.perform(get("/users/me").cookie(mariaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(maria.toString()));
    }

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/department-requests")).andExpect(status().isUnauthorized());
    }

    // ---------- auxiliares ----------

    private UUID register(String email, String cpf, String name, String departmentId) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "%s", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(name, cpf, email, PASSWORD, departmentId)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private MockHttpServletResponse login(String email) throws Exception {
        return loginExpecting(email, 200);
    }

    private MockHttpServletResponse loginExpecting(String email, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(email, PASSWORD)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse();
    }

    private static Cookie accessCookie(MockHttpServletResponse login) {
        return new Cookie("access_token", cookie(login, "access_token"));
    }

    private static String cookie(MockHttpServletResponse response, String name) {
        String header = response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith(name + "=")).findFirst().orElseThrow();
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private UUID ticket(UUID requester, UUID assignee) {
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id, assigned_at)
                VALUES ('Impressora', 'Sem papel', 'IN_PROGRESS', ?, 2, ?, ?, now()) RETURNING id
                """, UUID.class, category, requester, assignee);
    }
}
