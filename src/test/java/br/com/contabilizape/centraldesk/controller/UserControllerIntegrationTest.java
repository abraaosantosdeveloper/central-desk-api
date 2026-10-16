package br.com.contabilizape.centraldesk.controller;

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
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /users/me: só responde com um access token válido no cookie.
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
        jdbc.update("DELETE FROM department_requests");
        jdbc.update("DELETE FROM users WHERE id NOT IN (SELECT user_id FROM admins)");
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
                .andExpect(jsonPath("$.departmentName").value("Geral"))
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
                .andExpect(jsonPath("$.departmentName").value("Geral"))
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
        String setCookie = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        Matcher matcher = TOKEN_IN_COOKIE.matcher(setCookie);
        assertThat(matcher.find()).isTrue();
        return new Cookie("access_token", matcher.group(1));
    }

    // Token montado fora da API, para simular chave errada ou token vencido
    private static String token(String secret, Clock clock, UUID userId) {
        JwtConfig config = new JwtConfig();
        JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(15), "centraldesk");
        TokenService tokenService = new TokenService(config.jwtEncoder(config.jwtSigningKey(properties)), properties, clock);
        return tokenService.issueAccessToken(userId, EMAIL, List.of());
    }
}
