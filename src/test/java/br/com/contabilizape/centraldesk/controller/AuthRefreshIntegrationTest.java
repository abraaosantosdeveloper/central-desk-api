package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /auth/refresh: rotação do refresh token, detecção de reuso e revogação no logout.
 */
@IntegrationTest
class AuthRefreshIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String EMAIL = "maria.silva@contabilizape.com.br";
    private static final String PASSWORD = "senhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void registerMaria() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Maria da Silva", "cpf": "52998224725", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(EMAIL, PASSWORD, GENERAL_ID)))
                .andExpect(status().isCreated());
    }

    @Test
    void loginIssuesRefreshCookieOnlyForAuthRoutes() throws Exception {
        String refreshHeader = setCookie(login(), "refresh_token");

        assertThat(refreshHeader)
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/auth;", "Max-Age=604800");
    }

    @Test
    void onlyTheHashIsStored() throws Exception {
        String refreshToken = cookieValue(setCookie(login(), "refresh_token"));

        String storedHash = jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class);
        assertThat(storedHash).hasSize(64).isNotEqualTo(refreshToken);
    }

    @Test
    void refreshRotatesBothCookies() throws Exception {
        MockHttpServletResponse loginResponse = login();
        String oldRefresh = cookieValue(setCookie(loginResponse, "refresh_token"));

        MockHttpServletResponse refreshResponse = mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(oldRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andReturn().getResponse();

        String newRefresh = cookieValue(setCookie(refreshResponse, "refresh_token"));
        String newAccess = cookieValue(setCookie(refreshResponse, "access_token"));
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        // O access token novo funciona
        mockMvc.perform(get("/users/me").cookie(new Cookie("access_token", newAccess))).andExpect(status().isOk());
        // Uma sessão ativa (a nova) e uma revogada (a antiga)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void reusingARotatedTokenRevokesEverySession() throws Exception {
        String first = cookieValue(setCookie(login(), "refresh_token"));
        String second = cookieValue(setCookie(
                mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(first))).andExpect(status().isOk())
                        .andReturn().getResponse(), "refresh_token"));

        // Alguém reapresenta o token antigo (roubado): recusado...
        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(first))).andExpect(status().isUnauthorized());
        // ...e o token legítimo mais novo também cai
        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(second))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class)).isZero();
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRefreshTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie("token-inventado")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIsUnauthorized() throws Exception {
        String refresh = cookieValue(setCookie(login(), "refresh_token"));
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute'");

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(refresh))).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedAccountCannotRefresh() throws Exception {
        String refresh = cookieValue(setCookie(login(), "refresh_token"));
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE email = ?", EMAIL);

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(refresh))).andExpect(status().isForbidden());
    }

    @Test
    void refreshWorksEvenWithAnExpiredAccessTokenCookie() throws Exception {
        String refresh = cookieValue(setCookie(login(), "refresh_token"));

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("access_token", "token-vencido"), refreshCookie(refresh)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshPicksUpRolesGrantedAfterLogin() throws Exception {
        String refresh = cookieValue(setCookie(login(), "refresh_token"));
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, EMAIL);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", id);
        try {
            mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(refresh)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles", contains("ROLE_ADMIN")));
        } finally {
            // A limpeza padrão preserva administradores
            jdbc.update("DELETE FROM admins WHERE user_id = ?", id);
        }
    }

    @Test
    void refreshTokenIsUselessAfterLogout() throws Exception {
        String refresh = cookieValue(setCookie(login(), "refresh_token"));

        mockMvc.perform(post("/auth/logout").cookie(refreshCookie(refresh))).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class)).isZero();
        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie(refresh))).andExpect(status().isUnauthorized());
    }

    // ---------- auxiliares ----------

    private MockHttpServletResponse login() throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private static String setCookie(MockHttpServletResponse response, String name) {
        List<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        return headers.stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Set-Cookie de " + name + " ausente em " + headers));
    }

    private static String cookieValue(String setCookieHeader) {
        Matcher matcher = Pattern.compile("^[^=]+=([^;]*)").matcher(setCookieHeader);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static Cookie refreshCookie(String value) {
        return new Cookie("refresh_token", value);
    }
}
