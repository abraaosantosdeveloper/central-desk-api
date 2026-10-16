package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /auth/logout: apaga o cookie do access token e responde 204, com ou sem sessão válida.
 */
@IntegrationTest
class AuthLogoutIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
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
    void logoutWithSessionClearsTheCookie() throws Exception {
        Cookie session = registerAndLogin();
        // A sessão funciona antes do logout
        mockMvc.perform(get("/users/me").cookie(session)).andExpect(status().isOk());

        expectCookieCleared(mockMvc.perform(post("/auth/logout").cookie(session)));
    }

    @Test
    void logoutWithoutCookieStillSucceeds() throws Exception {
        expectCookieCleared(mockMvc.perform(post("/auth/logout")));
    }

    @Test
    void logoutWithExpiredOrInvalidCookieStillSucceeds() throws Exception {
        expectCookieCleared(mockMvc.perform(post("/auth/logout").cookie(new Cookie("access_token", "token-vencido"))));
    }

    // ---------- auxiliares ----------

    private static void expectCookieCleared(ResultActions result) throws Exception {
        result.andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, startsWith("access_token=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
    }

    private Cookie registerAndLogin() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Maria da Silva", "cpf": "52998224725", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(EMAIL, PASSWORD, GENERAL_ID)))
                .andExpect(status().isCreated());
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
}
