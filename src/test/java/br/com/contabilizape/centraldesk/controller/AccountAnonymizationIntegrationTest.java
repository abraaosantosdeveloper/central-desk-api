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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Encerramento de conta (RF04): DELETE /users/me pelo próprio usuário e
 * PATCH /users/{id}/anonymize pelo administrador (só contas desativadas).
 */
@IntegrationTest
class AccountAnonymizationIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String PASSWORD = "senhaForte123";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // A limpeza padrão preserva administradores; os criados aqui saem ao final
    @AfterEach
    void removeTestAdmins() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // ---------- DELETE /users/me ----------

    @Test
    void ownAccountIsAnonymizedAndEverythingRelatedIsCleaned() throws Exception {
        UUID maria = register(MARIA, "52998224725");
        UUID requester = register("joao.souza@contabilizape.com.br", "11144477735");
        UUID inProgress = ticket(requester, "IN_PROGRESS", maria);
        UUID closed = ticket(requester, "CLOSED", maria);
        jdbc.update("INSERT INTO notifications (recipient_id, type, ticket_id) VALUES (?, 'TICKET_COMMENTED', ?)", maria, inProgress);
        MockHttpServletResponse login = login(MARIA);
        String otherSessionRefresh = refreshToken(login(MARIA));

        MockHttpServletResponse response = mockMvc.perform(delete("/users/me").cookie(accessCookie(login))
                        .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("access_token=;"))
                .anyMatch(cookie -> cookie.startsWith("refresh_token=;"));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM users WHERE id = ?", maria);
        assertThat(row.get("full_name")).isEqualTo("Usuário removido");
        assertThat(row.get("cpf")).isNull();
        assertThat(row.get("email")).isEqualTo("anonimizado-" + maria + "@anonimizado.invalid");
        assertThat(row.get("status")).isEqualTo("ANONYMIZED");
        assertThat(row.get("deleted_at")).isNotNull();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, maria)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipient_id = ?", Integer.class, maria)).isZero();
        assertThat(jdbc.queryForMap("SELECT status, assignee_id FROM tickets WHERE id = ?", inProgress))
                .containsEntry("status", "OPEN").containsEntry("assignee_id", null);
        // Chamado encerrado guarda o histórico
        assertThat(jdbc.queryForObject("SELECT assignee_id FROM tickets WHERE id = ?", UUID.class, closed)).isEqualTo(maria);

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", otherSessionRefresh)))
                .andExpect(status().isUnauthorized());
        loginExpecting(MARIA, 401);
        // O CPF e o e-mail ficam livres para um novo cadastro
        register(MARIA, "52998224725");
    }

    @Test
    void ownAccountWithWrongPasswordIsBadRequest() throws Exception {
        register(MARIA, "52998224725");

        mockMvc.perform(delete("/users/me").cookie(accessCookie(login(MARIA)))
                        .contentType(MediaType.APPLICATION_JSON).content(passwordBody("senhaErrada99")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        loginExpecting(MARIA, 200);
    }

    @Test
    void ownAccountWithoutPasswordIsBadRequest() throws Exception {
        register(MARIA, "52998224725");

        mockMvc.perform(delete("/users/me").cookie(accessCookie(login(MARIA)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void ownAccountWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/users/me").contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lastAdminCannotCloseOwnAccount() throws Exception {
        UUID admin = register(ADMIN, "11144477735");
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);

        mockMvc.perform(delete("/users/me").cookie(accessCookie(login(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content(passwordBody(PASSWORD)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, admin)).isEqualTo("ACTIVE");
    }

    // ---------- PATCH /users/{id}/anonymize ----------

    @Test
    void adminAnonymizesDeactivatedAccount() throws Exception {
        UUID maria = register(MARIA, "52998224725");
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE id = ?", maria);

        mockMvc.perform(patch("/users/" + maria + "/anonymize").cookie(adminCookie()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, maria)).isEqualTo("ANONYMIZED");
    }

    @Test
    void adminCannotAnonymizeActiveAccount() throws Exception {
        UUID maria = register(MARIA, "52998224725");

        mockMvc.perform(patch("/users/" + maria + "/anonymize").cookie(adminCookie()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, maria)).isEqualTo("ACTIVE");
    }

    @Test
    void adminAnonymizingUnknownUserIsNotFound() throws Exception {
        mockMvc.perform(patch("/users/" + UUID.randomUUID() + "/anonymize").cookie(adminCookie()))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        UUID maria = register(MARIA, "52998224725");
        register("joao.souza@contabilizape.com.br", "11144477735");
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE id = ?", maria);

        mockMvc.perform(patch("/users/" + maria + "/anonymize").cookie(accessCookie(login("joao.souza@contabilizape.com.br"))))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, maria)).isEqualTo("DEACTIVATED");
    }

    @Test
    void anonymizeWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/users/" + UUID.randomUUID() + "/anonymize"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- auxiliares ----------

    private UUID register(String email, String cpf) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Maria da Silva", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(cpf, email, PASSWORD, GENERAL_ID)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private Cookie adminCookie() throws Exception {
        UUID admin = register(ADMIN, "11144477735");
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        return accessCookie(login(ADMIN));
    }

    private UUID ticket(UUID requester, String status, UUID assignee) {
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id, assigned_at)
                VALUES ('Impressora', 'Sem papel', ?, ?, 2, ?, ?, now()) RETURNING id
                """, UUID.class, status, category, requester, assignee);
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

    private static String refreshToken(MockHttpServletResponse login) {
        return cookie(login, "refresh_token");
    }

    private static String cookie(MockHttpServletResponse response, String name) {
        String header = response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith(name + "=")).findFirst().orElseThrow();
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    private static String passwordBody(String password) {
        return "{ \"password\": \"%s\" }".formatted(password);
    }
}
