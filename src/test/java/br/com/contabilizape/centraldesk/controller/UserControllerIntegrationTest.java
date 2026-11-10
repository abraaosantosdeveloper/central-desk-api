package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
import br.com.contabilizape.centraldesk.security.JwtConfig;
import br.com.contabilizape.centraldesk.security.JwtProperties;
import br.com.contabilizape.centraldesk.security.TokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /users/me: dados do usuário logado, edição do nome e troca de senha. Só responde com um access token válido no cookie.
 */
@IntegrationTest
class UserControllerIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String EMAIL = "maria.silva@contabilizape.com.br";
    private static final String PASSWORD = "senhaForte123";
    private static final Pattern TOKEN_IN_COOKIE = Pattern.compile("access_token=([^;]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void withoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validSessionReturnsTheLoggedUser() throws Exception {
        register(GENERAL_ID);

        mockMvc.perform(get("/users/me").cookie(loginCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Maria da Silva"))
                .andExpect(jsonPath("$.departmentName").value("Comunidade"))
                .andExpect(jsonPath("$.departmentApprovalPending").value(false))
                .andExpect(jsonPath("$.roles", empty()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.cpf").doesNotExist());
    }

    @Test
    void pendingInnovationRequestIsShown() throws Exception {
        register(INNOVATION_ID);

        mockMvc.perform(get("/users/me").cookie(loginCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Comunidade"))
                .andExpect(jsonPath("$.departmentApprovalPending").value(true));
    }

    @Test
    void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me").cookie(new Cookie("access_token", "isto-nao-e-um-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        UUID id = register(GENERAL_ID);
        String otherSecret = Base64.getEncoder().encodeToString("uma-chave-diferente-de-32-bytes!".getBytes());

        mockMvc.perform(get("/users/me").cookie(new Cookie("access_token", token(otherSecret, Clock.systemUTC(), id))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        UUID id = register(GENERAL_ID);
        Clock oneHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);

        mockMvc.perform(get("/users/me").cookie(new Cookie("access_token", token(IntegrationTest.JWT_SECRET, oneHourAgo, id))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountDeactivatedAfterLoginIsForbidden() throws Exception {
        register(GENERAL_ID);
        Cookie session = loginCookie();
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE email = ?", EMAIL);

        mockMvc.perform(get("/users/me").cookie(session))
                .andExpect(status().isForbidden());
    }

    // ---------- PUT /users/me ----------

    @Test
    void updateProfileChangesOnlyTheName() throws Exception {
        register(GENERAL_ID);

        mockMvc.perform(put("/users/me").cookie(loginCookie()).contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Maria Souza", "email": "outra@contabilizape.com.br", "cpf": "11144477735" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Maria Souza"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.departmentName").value("Comunidade"));

        assertThat(jdbc.queryForObject("SELECT full_name FROM users WHERE email = ?", String.class, EMAIL))
                .isEqualTo("Maria Souza");
        assertThat(jdbc.queryForObject("SELECT cpf FROM users WHERE email = ?", String.class, EMAIL))
                .isEqualTo("52998224725");
    }

    @Test
    void updateProfileWithBlankOrTooLongNameIsBadRequest() throws Exception {
        register(GENERAL_ID);
        Cookie session = loginCookie();

        for (String name : List.of("   ", "a".repeat(151))) {
            mockMvc.perform(put("/users/me").cookie(session).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"fullName\": \"%s\" }".formatted(name)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.fullName").exists());
        }
    }

    @Test
    void updateProfileWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content("{ \"fullName\": \"Maria\" }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfileOfDeactivatedAccountIsForbidden() throws Exception {
        register(GENERAL_ID);
        Cookie session = loginCookie();
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE email = ?", EMAIL);

        mockMvc.perform(put("/users/me").cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"fullName\": \"Maria\" }"))
                .andExpect(status().isForbidden());
    }

    // ---------- PATCH /users/me/password ----------

    @Test
    void changePasswordRenewsTheCurrentSessionAndEndsTheOthers() throws Exception {
        register(GENERAL_ID);
        String otherSessionRefresh = refreshToken(login(PASSWORD));

        MockHttpServletResponse response = mockMvc.perform(patch("/users/me/password").cookie(loginCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(passwordChange(PASSWORD, "novaSenha456")))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        // A sessão atual recebe cookies novos e continua renovando
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(cookie -> cookie.startsWith("access_token=") && !cookie.startsWith("access_token=;"));
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", refreshToken(response))))
                .andExpect(status().isOk());
        // O access token novo vale (foi emitido depois do corte de sessões gravado na troca)
        String newAccess = response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("access_token=")).findFirst().orElseThrow();
        mockMvc.perform(get("/users/me")
                        .cookie(new Cookie("access_token", newAccess.substring(newAccess.indexOf('=') + 1, newAccess.indexOf(';')))))
                .andExpect(status().isOk());

        // A outra sessão não renova mais
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", otherSessionRefresh)))
                .andExpect(status().isUnauthorized());

        // Só a senha nova entra
        loginExpecting(PASSWORD, 401);
        loginExpecting("novaSenha456", 200);
    }

    @Test
    void changePasswordWithWrongCurrentPasswordIsBadRequest() throws Exception {
        register(GENERAL_ID);

        mockMvc.perform(patch("/users/me/password").cookie(loginCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChange("senhaErrada99", "novaSenha456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").exists());
        loginExpecting(PASSWORD, 200);
    }

    @Test
    void changePasswordToTheSamePasswordIsBadRequest() throws Exception {
        register(GENERAL_ID);

        mockMvc.perform(patch("/users/me/password").cookie(loginCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChange(PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    void changePasswordToAWeakPasswordIsBadRequest() throws Exception {
        register(GENERAL_ID);

        mockMvc.perform(patch("/users/me/password").cookie(loginCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChange(PASSWORD, "semnumero")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        loginExpecting(PASSWORD, 200);
    }

    @Test
    void changePasswordWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChange(PASSWORD, "novaSenha456")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- auxiliares ----------

    private UUID register(String departmentId) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Maria da Silva", "cpf": "52998224725", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(EMAIL, PASSWORD, departmentId)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, EMAIL);
    }

    private Cookie loginCookie() throws Exception {
        String setCookie = login(PASSWORD).getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        Matcher matcher = TOKEN_IN_COOKIE.matcher(setCookie);
        assertThat(matcher.find()).isTrue();
        return new Cookie("access_token", matcher.group(1));
    }

    private MockHttpServletResponse login(String password) throws Exception {
        return loginExpecting(password, 200).andReturn().getResponse();
    }

    private ResultActions loginExpecting(String password, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(EMAIL, password)))
                .andExpect(status().is(expectedStatus));
    }

    private static String refreshToken(MockHttpServletResponse response) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("refresh_token="))
                .findFirst().map(UserControllerIntegrationTest::cookieValue).orElseThrow();
    }

    private static String cookieValue(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private static String passwordChange(String currentPassword, String newPassword) {
        return """
                { "currentPassword": "%s", "newPassword": "%s" }
                """.formatted(currentPassword, newPassword);
    }

    // Token montado fora da API, para simular chave errada ou token vencido
    private static String token(String secret, Clock clock, UUID userId) {
        JwtConfig config = new JwtConfig();
        JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(15), "centraldesk");
        TokenService tokenService = new TokenService(config.jwtEncoder(config.jwtSigningKey(properties)), properties, clock);
        return tokenService.issueAccessToken(userId, EMAIL, List.of());
    }
}
