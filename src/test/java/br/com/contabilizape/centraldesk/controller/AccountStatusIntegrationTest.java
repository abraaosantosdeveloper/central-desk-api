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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-200: estados novos da conta (BLOCKED, PENDING_VERIFICATION) e os campos de segurança da V18.
 */
@IntegrationTest
class AccountStatusIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String PASSWORD = "senhaForte123";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String JOAO = "joao.souza@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie adminSession;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        UUID admin = register(ADMIN, "71428793860", "Ana Admin");
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        adminSession = login(ADMIN);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void newAccountStartsWithTheSecurityDefaults() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva");

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT failed_login_attempts, blocked_at, two_factor_enabled, password_change_required,
                       temporary_password_expires_at FROM users WHERE id = ?""", maria);
        assertThat(((Number) row.get("failed_login_attempts")).intValue()).isZero();
        assertThat(row.get("blocked_at")).isNull();
        assertThat(row.get("two_factor_enabled")).isEqualTo(true);
        assertThat(row.get("password_change_required")).isEqualTo(false);
        assertThat(row.get("temporary_password_expires_at")).isNull();
    }

    @Test
    void blockedAccountsHaveTheirOwnFilterAndCanStillBeDeactivated() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva");
        jdbc.update("UPDATE users SET status = 'BLOCKED', blocked_at = now() WHERE id = ?", maria);

        mockMvc.perform(get("/users").param("status", "BLOCKED").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].email", contains(MARIA)))
                .andExpect(jsonPath("$.content[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$.content[0].blockedAt").exists());

        // A pessoa saiu da empresa enquanto a conta estava bloqueada
        mockMvc.perform(patch("/users/" + maria + "/deactivate").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
    }

    @Test
    void accountsWaitingForEmailConfirmationAreNotListedByDefault() throws Exception {
        register(MARIA, "52998224725", "Maria da Silva");
        UUID joao = register(JOAO, "11144477735", "João Souza");
        jdbc.update("UPDATE users SET status = 'PENDING_VERIFICATION', email_verified_at = NULL WHERE id = ?", joao);

        mockMvc.perform(get("/users").cookie(adminSession))
                .andExpect(jsonPath("$.content[*].email", contains(ADMIN, MARIA)));
        mockMvc.perform(get("/users").param("status", "PENDING_VERIFICATION").cookie(adminSession))
                .andExpect(jsonPath("$.content[*].email", contains(JOAO)));
    }

    @Test
    void blockedAccountWithAnOpenSessionIsRefused() throws Exception {
        register(MARIA, "52998224725", "Maria da Silva");
        Cookie session = login(MARIA);
        jdbc.update("UPDATE users SET status = 'BLOCKED', blocked_at = now() WHERE email = ?", MARIA);

        mockMvc.perform(get("/tickets/mine").cookie(session)).andExpect(status().isForbidden());
    }

    // ---------- auxiliares ----------

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
