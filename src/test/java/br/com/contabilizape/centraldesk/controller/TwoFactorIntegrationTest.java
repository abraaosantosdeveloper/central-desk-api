package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.RecordingEmailSender;
import br.com.contabilizape.centraldesk.SecurityIntegrationTest;
import br.com.contabilizape.centraldesk.TestAccounts;
import br.com.contabilizape.centraldesk.security.LoginAttemptLimiter;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static br.com.contabilizape.centraldesk.TestAccounts.PASSWORD;
import static br.com.contabilizape.centraldesk.TestAccounts.cookie;
import static br.com.contabilizape.centraldesk.TestAccounts.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-206: verificação em duas etapas por e-mail e navegadores confiáveis (PLANO-SEGURANCA §9).
 */
@SecurityIntegrationTest
class TwoFactorIntegrationTest {

    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingEmailSender emails;
    @Autowired private LoginAttemptLimiter limiter;

    private UUID maria;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        limiter.clear();
        maria = TestAccounts.activeAccount(mockMvc, jdbc, MARIA, "52998224725", "Maria da Silva", true);
        emails.clear();
    }

    @AfterEach
    void cleanUp() {
        limiter.clear();
        TestAccounts.removeAdmins(jdbc, ADMIN);
    }

    // ---------- login ----------

    @Test
    void rightPasswordAsksForTheCodeAndOpensNoSession() throws Exception {
        MockHttpServletResponse response = login(mockMvc, MARIA, PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tfaRequired").value(true))
                .andReturn().getResponse();

        assertThat(cookie(response, "access_token")).isNull();
        assertThat(cookie(response, "refresh_token")).isNull();
        assertThat(setCookie(response, "tfa_challenge")).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth",
                "Max-Age=300");
        assertThat(emails.subjectsTo(MARIA)).containsExactly("Seu código de acesso");
    }

    @Test
    void rightCodeOpensTheSessionAndTrustsThisBrowserFor30Days() throws Exception {
        MockHttpServletResponse response = completeLogin(true).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(MARIA))
                .andReturn().getResponse();

        assertThat(cookie(response, "access_token")).isNotNull();
        assertThat(setCookie(response, "trusted_device")).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth",
                "Max-Age=2592000");
        assertThat(emails.subjectsTo(MARIA)).contains("Novo acesso à sua conta");
        assertThat(emails.to(MARIA).getLast().html()).contains("Chrome no Windows");

        // O mesmo navegador entra direto; outro navegador precisa do código de novo
        login(mockMvc, MARIA, PASSWORD, cookie(response, "trusted_device")).andExpect(status().isOk());
        login(mockMvc, MARIA, PASSWORD).andExpect(status().isAccepted());
    }

    @Test
    void withoutTrustingTheBrowserTheNextLoginAsksAgain() throws Exception {
        MockHttpServletResponse response = completeLogin(false).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(cookie(response, "trusted_device")).isNull();
        login(mockMvc, MARIA, PASSWORD).andExpect(status().isAccepted());
    }

    @Test
    void wrongCodeCountsForTheLockoutAndWarnsTheOwnerOnce() throws Exception {
        Cookie challenge = challenge();

        submitCode(challenge, "000000", false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Código inválido ou vencido."));
        submitCode(challenge, "000001", false).andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, maria)).isEqualTo(2);
        assertThat(emails.subjectsTo(MARIA)).containsOnlyOnce("Tentativa de acesso à sua conta");
    }

    @Test
    void passwordRightButFiveWrongCodesBlockTheAccount() throws Exception {
        Cookie challenge = challenge();

        for (int i = 0; i < 5; i++) {
            submitCode(challenge, "00000" + i, false).andExpect(status().isBadRequest());
        }

        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, maria)).isEqualTo("BLOCKED");
        login(mockMvc, MARIA, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account-blocked"));
    }

    @Test
    void challengeWorksOnlyOnceAndNotWithoutTheCookie() throws Exception {
        Cookie challenge = challenge();
        String code = emails.lastCodeTo(MARIA);

        submitCode(challenge, code, false).andExpect(status().isOk());
        submitCode(challenge, code, false).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login/tfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"code\": \"%s\", \"trustThisBrowser\": false }".formatted(code)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredChallengeIsRefused() throws Exception {
        Cookie challenge = challenge();
        jdbc.update("UPDATE login_challenges SET expires_at = now() - interval '1 second'");

        submitCode(challenge, emails.lastCodeTo(MARIA), false).andExpect(status().isUnauthorized());
    }

    @Test
    void codeCanBeSentAgain() throws Exception {
        Cookie challenge = challenge();
        jdbc.update("UPDATE verification_codes SET created_at = created_at - interval '2 minutes'");

        mockMvc.perform(post("/auth/login/tfa/resend").cookie(challenge)).andExpect(status().isAccepted());

        assertThat(emails.subjectsTo(MARIA)).containsExactly("Seu código de acesso", "Seu código de acesso");
        submitCode(challenge, emails.lastCodeTo(MARIA), false).andExpect(status().isOk());
    }

    // ---------- navegadores confiáveis ----------

    @Test
    void trustedBrowsersAreListedAndCanBeRemoved() throws Exception {
        MockHttpServletResponse response = completeLogin(true).andReturn().getResponse();
        Cookie session = cookie(response, "access_token");
        Cookie trusted = cookie(response, "trusted_device");

        String body = mockMvc.perform(get("/users/me/trusted-devices").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].label", contains("Chrome no Windows")))
                .andExpect(jsonPath("$[0].expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$[0].id");

        mockMvc.perform(delete("/users/me/trusted-devices/" + id).cookie(session)).andExpect(status().isNoContent());

        login(mockMvc, MARIA, PASSWORD, trusted).andExpect(status().isAccepted());
    }

    @Test
    void changingThePasswordRequiresACodeAndForgetsTheTrustedBrowsers() throws Exception {
        MockHttpServletResponse response = completeLogin(true).andReturn().getResponse();
        Cookie session = cookie(response, "access_token");
        Cookie trusted = cookie(response, "trusted_device");

        changePassword(session, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists());

        jdbc.update("UPDATE verification_codes SET created_at = created_at - interval '2 minutes'");
        mockMvc.perform(post("/users/me/password/code").cookie(session)).andExpect(status().isAccepted());
        changePassword(session, emails.lastCodeTo(MARIA)).andExpect(status().isNoContent());

        login(mockMvc, MARIA, "senhaNova2026", trusted).andExpect(status().isAccepted());
    }

    // ---------- perfil ----------

    @Test
    void ownerTurnsTwoFactorOffWithPasswordAndCode() throws Exception {
        Cookie session = cookie(completeLogin(false).andReturn().getResponse(), "access_token");

        mockMvc.perform(get("/users/me/two-factor").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.mandatory").value(false));

        jdbc.update("UPDATE verification_codes SET created_at = created_at - interval '2 minutes'");
        mockMvc.perform(post("/users/me/two-factor/code").cookie(session)).andExpect(status().isAccepted());
        String code = emails.lastCodeTo(MARIA);

        turnOff(session, "senhaErrada1", code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").exists());
        turnOff(session, PASSWORD, code).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));

        assertThat(emails.subjectsTo(MARIA)).contains("Verificação em duas etapas desligada");
        login(mockMvc, MARIA, PASSWORD).andExpect(status().isOk());

        mockMvc.perform(put("/users/me/two-factor").cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"enabled\": true }"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        login(mockMvc, MARIA, PASSWORD).andExpect(status().isAccepted());
    }

    @Test
    void turningOffWithoutTheCodeIsRefused() throws Exception {
        Cookie session = cookie(completeLogin(false).andReturn().getResponse(), "access_token");

        turnOff(session, PASSWORD, "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists());
        assertThat(jdbc.queryForObject("SELECT two_factor_enabled FROM users WHERE id = ?", Boolean.class, maria)).isTrue();
    }

    @Test
    void adminsCannotTurnTwoFactorOff() throws Exception {
        TestAccounts.activeAdmin(mockMvc, jdbc, ADMIN, "71428793860", "Ana Admin");
        jdbc.update("UPDATE users SET two_factor_enabled = false WHERE email = ?", ADMIN);
        // Mesmo com o interruptor desligado no banco, o login de admin pede o código
        login(mockMvc, ADMIN, PASSWORD).andExpect(status().isAccepted());
        Cookie session = TestAccounts.signIn(mockMvc, emails, ADMIN, PASSWORD);

        mockMvc.perform(get("/users/me/two-factor").cookie(session))
                .andExpect(jsonPath("$.mandatory").value(true));
        turnOff(session, PASSWORD, "123456").andExpect(status().isConflict());
    }

    // ---------- auxiliares ----------

    private Cookie challenge() throws Exception {
        MockHttpServletResponse response = login(mockMvc, MARIA, PASSWORD).andExpect(status().isAccepted())
                .andReturn().getResponse();
        return cookie(response, "tfa_challenge");
    }

    private ResultActions completeLogin(boolean trust) throws Exception {
        return submitCode(challenge(), emails.lastCodeTo(MARIA), trust);
    }

    private ResultActions submitCode(Cookie challenge, String code, boolean trust) throws Exception {
        return mockMvc.perform(post("/auth/login/tfa").cookie(challenge)
                .header(HttpHeaders.USER_AGENT, CHROME_WINDOWS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"code\": \"%s\", \"trustThisBrowser\": %s }".formatted(code, trust)));
    }

    private ResultActions changePassword(Cookie session, String code) throws Exception {
        String codeField = code == null ? "" : ", \"code\": \"%s\"".formatted(code);
        return mockMvc.perform(patch("/users/me/password").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"currentPassword\": \"%s\", \"newPassword\": \"senhaNova2026\"%s }".formatted(PASSWORD, codeField)));
    }

    private ResultActions turnOff(Cookie session, String password, String code) throws Exception {
        return mockMvc.perform(put("/users/me/two-factor").cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{ \"enabled\": false, \"currentPassword\": \"%s\", \"code\": \"%s\" }".formatted(password, code)));
    }

    private static String setCookie(MockHttpServletResponse response, String name) {
        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        return headers.stream().filter(header -> header.startsWith(name + "=")).findFirst()
                .orElseThrow(() -> new AssertionError("Sem Set-Cookie " + name + ": " + headers));
    }
}
