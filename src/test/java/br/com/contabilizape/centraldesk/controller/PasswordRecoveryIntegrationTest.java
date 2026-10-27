package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.RecordingEmailSender;
import br.com.contabilizape.centraldesk.SecurityIntegrationTest;
import br.com.contabilizape.centraldesk.TestAccounts;
import br.com.contabilizape.centraldesk.security.LoginAttemptLimiter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static br.com.contabilizape.centraldesk.TestAccounts.PASSWORD;
import static br.com.contabilizape.centraldesk.TestAccounts.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-204: "Esqueci minha senha" (PLANO-SEGURANCA §7). Resposta sempre igual (B7); conta bloqueada
 * não redefine pelo e-mail (S1); o código errado não conta para o bloqueio.
 */
@SecurityIntegrationTest
class PasswordRecoveryIntegrationTest {

    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String JOAO = "joao.souza@contabilizape.com.br";
    private static final String NEW_PASSWORD = "outraSenha2026";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingEmailSender emails;
    @Autowired private LoginAttemptLimiter limiter;

    private UUID maria;
    private UUID joao;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        limiter.clear();
        maria = TestAccounts.activeAccount(mockMvc, jdbc, MARIA, "52998224725", "Maria da Silva", false);
        joao = TestAccounts.activeAccount(mockMvc, jdbc, JOAO, "11144477735", "João Souza", false);
        jdbc.update("UPDATE users SET status = 'BLOCKED', blocked_at = now(), failed_login_attempts = 5 WHERE id = ?", joao);
        emails.clear();
    }

    @AfterEach
    void cleanUp() {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void answerIsTheSameWhetherTheAccountExistsIsBlockedOrNot() throws Exception {
        String active = forgot(MARIA).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String blocked = forgot(JOAO).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = forgot("ninguem@contabilizape.com.br").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(active).isEqualTo(blocked).isEqualTo(unknown);
        // Por trás: código para a conta ativa; aviso sem código para a bloqueada; nada para quem não existe
        assertThat(emails.subjectsTo(MARIA)).containsExactly("Código para redefinir sua senha");
        assertThat(emails.subjectsTo(JOAO)).containsExactly("Sua conta está bloqueada");
        assertThat(emails.to(JOAO).getFirst().html()).doesNotContainPattern(">\\d{6}</p>");
        assertThat(emails.subjectsTo("ninguem@contabilizape.com.br")).isEmpty();
    }

    @Test
    void rightCodeSetsTheNewPasswordAndEndsEverySession() throws Exception {
        Cookie oldSession = TestAccounts.signIn(mockMvc, emails, MARIA, PASSWORD);
        // O corte de sessões vale a partir do segundo seguinte ao da emissão do token (SessionCutoffValidator)
        Thread.sleep(1100);
        forgot(MARIA).andExpect(status().isAccepted());

        reset(MARIA, emails.lastCodeTo(MARIA), NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, maria)).isZero();
        mockMvc.perform(get("/tickets/mine").cookie(oldSession))
                .andExpect(status().isUnauthorized());
        login(mockMvc, MARIA, PASSWORD).andExpect(status().isUnauthorized());
        login(mockMvc, MARIA, NEW_PASSWORD).andExpect(status().isOk());
        assertThat(emails.subjectsTo(MARIA)).contains("Sua senha foi alterada");
    }

    @Test
    void codeWorksOnlyOnce() throws Exception {
        forgot(MARIA).andExpect(status().isAccepted());
        String code = emails.lastCodeTo(MARIA);

        reset(MARIA, code, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(MARIA, code, "maisUmaSenha77").andExpect(status().isBadRequest());
    }

    @Test
    void wrongCodeDoesNotTellWhyAndDoesNotCountForTheLockout() throws Exception {
        forgot(MARIA).andExpect(status().isAccepted());

        for (int i = 0; i < 6; i++) {
            reset(MARIA, "000000", NEW_PASSWORD)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.code").value("Código inválido ou vencido."));
        }

        assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, maria)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, maria)).isEqualTo("ACTIVE");
        login(mockMvc, MARIA, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void blockedAccountCannotResetEvenWithAGuessedCode() throws Exception {
        reset(JOAO, "123456", NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Código inválido ou vencido."));
        reset("ninguem@contabilizape.com.br", "123456", NEW_PASSWORD).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, joao)).isEqualTo("BLOCKED");
    }

    @Test
    void newPasswordFollowsTheRegistrationRules() throws Exception {
        forgot(MARIA).andExpect(status().isAccepted());

        reset(MARIA, emails.lastCodeTo(MARIA), "curta").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    void askingAgainRightAwayStillAnswersTheSameButSendsNothingNew() throws Exception {
        forgot(MARIA).andExpect(status().isAccepted());
        forgot(MARIA).andExpect(status().isAccepted());

        assertThat(emails.subjectsTo(MARIA)).hasSize(1);
    }

    // ---------- auxiliares ----------

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\" }".formatted(email)));
    }

    private ResultActions reset(String email, String code, String newPassword) throws Exception {
        return mockMvc.perform(post("/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\", \"code\": \"%s\", \"newPassword\": \"%s\" }".formatted(email, code, newPassword)));
    }
}
