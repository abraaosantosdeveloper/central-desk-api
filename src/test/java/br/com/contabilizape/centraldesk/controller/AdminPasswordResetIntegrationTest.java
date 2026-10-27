package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.RecordingEmailSender;
import br.com.contabilizape.centraldesk.SecurityIntegrationTest;
import br.com.contabilizape.centraldesk.TestAccounts;
import br.com.contabilizape.centraldesk.email.EmailMessage;
import br.com.contabilizape.centraldesk.security.LoginAttemptLimiter;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static br.com.contabilizape.centraldesk.TestAccounts.PASSWORD;
import static br.com.contabilizape.centraldesk.TestAccounts.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-203: redefinição de senha pelo administrador, com senha temporária e troca obrigatória
 * (PLANO-SEGURANCA §6).
 */
@SecurityIntegrationTest
class AdminPasswordResetIntegrationTest {

    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingEmailSender emails;
    @Autowired private LoginAttemptLimiter limiter;

    private UUID maria;
    private UUID admin;
    private Cookie adminSession;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        limiter.clear();
        maria = TestAccounts.activeAccount(mockMvc, jdbc, MARIA, "52998224725", "Maria da Silva", false);
        admin = TestAccounts.activeAdmin(mockMvc, jdbc, ADMIN, "71428793860", "Ana Admin");
        adminSession = TestAccounts.signIn(mockMvc, emails, ADMIN, PASSWORD);
        emails.clear();
    }

    @AfterEach
    void cleanUp() {
        TestAccounts.removeAdmins(jdbc, ADMIN);
    }

    @Test
    void adminUnblocksTheAccountWithATemporaryPasswordShownOnlyToHim() throws Exception {
        jdbc.update("UPDATE users SET status = 'BLOCKED', blocked_at = now(), failed_login_attempts = 5 WHERE id = ?", maria);

        String body = reset(maria).andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isString())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        String temporary = JsonPath.read(body, "$.temporaryPassword");

        assertThat(temporary).hasSize(16).matches(".*[A-Za-z].*").matches(".*\\d.*");
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT status, blocked_at, failed_login_attempts, password_change_required, temporary_password_expires_at,
                       password_hash FROM users WHERE id = ?""", maria);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("blocked_at")).isNull();
        assertThat(((Number) row.get("failed_login_attempts")).intValue()).isZero();
        assertThat(row.get("password_change_required")).isEqualTo(true);
        assertThat(row.get("temporary_password_expires_at")).isNotNull();
        assertThat(row.get("password_hash").toString()).doesNotContain(temporary);

        // O dono é avisado, mas a senha temporária nunca vai por e-mail
        assertThat(emails.subjectsTo(MARIA)).containsExactly("Sua senha foi redefinida pela equipe de Inovação");
        assertThat(emails.messages()).extracting(EmailMessage::html).noneMatch(html -> html.contains(temporary));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_events WHERE user_id = ? AND type = 'PASSWORD_RESET_BY_ADMIN'",
                Integer.class, maria)).isEqualTo(1);
    }

    @Test
    void firstAccessWithTheTemporaryPasswordOnlyAllowsChoosingANewOne() throws Exception {
        String temporary = temporaryPasswordOf(maria);

        MockHttpServletResponse response = login(mockMvc, MARIA, temporary).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("PASSWORD_CHANGE_REQUIRED")))
                .andReturn().getResponse();
        Cookie session = TestAccounts.cookie(response, "access_token");

        mockMvc.perform(get("/users/me").cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get("/tickets/mine").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("password-change-required"));
        mockMvc.perform(get("/categories").cookie(session)).andExpect(status().isForbidden());

        MockHttpServletResponse changed = mockMvc.perform(patch("/users/me/password").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"currentPassword\": \"%s\", \"newPassword\": \"minhaNovaSenha42\" }".formatted(temporary)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        Cookie fresh = TestAccounts.cookie(changed, "access_token");
        mockMvc.perform(get("/tickets/mine").cookie(fresh)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT password_change_required FROM users WHERE id = ?", Boolean.class, maria)).isFalse();
        login(mockMvc, MARIA, "minhaNovaSenha42").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void expiredTemporaryPasswordNoLongerWorks() throws Exception {
        String temporary = temporaryPasswordOf(maria);
        jdbc.update("UPDATE users SET temporary_password_expires_at = now() - interval '1 minute' WHERE id = ?", maria);

        login(mockMvc, MARIA, temporary).andExpect(status().isUnauthorized());
    }

    @Test
    void resetEndsTheSessionsOfTheAccount() throws Exception {
        Cookie mariaSession = TestAccounts.signIn(mockMvc, emails, MARIA, PASSWORD);
        // O corte de sessões vale a partir do segundo seguinte ao da emissão do token (SessionCutoffValidator)
        Thread.sleep(1100);

        reset(maria).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, maria)).isZero();
        mockMvc.perform(get("/tickets/mine").cookie(mariaSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidResetsAreRefused() throws Exception {
        // O próprio admin usa o perfil ou o "esqueci minha senha"
        reset(admin).andExpect(status().isConflict());

        jdbc.update("UPDATE users SET status = 'DEACTIVATED', deactivated_at = now() WHERE id = ?", maria);
        reset(maria).andExpect(status().isConflict());

        reset(UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsResetPasswords() throws Exception {
        UUID joao = TestAccounts.activeAccount(mockMvc, jdbc, "joao.souza@contabilizape.com.br", "11144477735", "João", false);
        Cookie collaborator = TestAccounts.signIn(mockMvc, emails, "joao.souza@contabilizape.com.br", PASSWORD);

        mockMvc.perform(put("/users/" + maria + "/password-reset").cookie(collaborator)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT password_change_required FROM users WHERE id = ?", Boolean.class, maria)).isFalse();
        assertThat(joao).isNotNull();
    }

    // ---------- auxiliares ----------

    private org.springframework.test.web.servlet.ResultActions reset(UUID userId) throws Exception {
        return mockMvc.perform(put("/users/" + userId + "/password-reset").cookie(adminSession));
    }

    private String temporaryPasswordOf(UUID userId) throws Exception {
        String body = reset(userId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.temporaryPassword");
    }

}
