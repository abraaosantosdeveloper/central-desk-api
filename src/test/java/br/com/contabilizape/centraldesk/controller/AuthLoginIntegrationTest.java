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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /auth/login de ponta a ponta: credenciais → bcrypt → JWT no cookie.
 */
@IntegrationTest
class AuthLoginIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String EMAIL = "maria.silva@contabilizape.com.br";
    private static final String PASSWORD = "senhaForte123";
    private static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos.";

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
    void validCredentialsSetHttpOnlyCookieAndReturnUser() throws Exception {
        mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, startsWith("access_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=900")))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Maria da Silva"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.departmentName").value("Comunidade"))
                .andExpect(jsonPath("$.roles", empty()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.cpf").doesNotExist())
                // O token só viaja no cookie: o JavaScript da página não tem acesso a ele
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        mockMvc.perform(login("  Maria.Silva@ContabilizaPE.com.br ", PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(login(EMAIL, "senhaErrada123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS));
    }

    @Test
    void unknownEmailGetsTheSameAnswerAsWrongPassword() throws Exception {
        mockMvc.perform(login("ninguem@contabilizape.com.br", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS));
    }

    @Test
    void inactiveAccountIsForbidden() throws Exception {
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE email = ?", EMAIL);

        mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void blankFieldsAreListed() throws Exception {
        mockMvc.perform(login("", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void staleCookieDoesNotBlockLogin() throws Exception {
        mockMvc.perform(login(EMAIL, PASSWORD).cookie(new Cookie("access_token", "token-vencido")))
                .andExpect(status().isOk());
    }

    @Test
    void staleCookieDoesNotBlockPublicDepartmentList() throws Exception {
        mockMvc.perform(get("/departments").cookie(new Cookie("access_token", "token-vencido")))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                { "email": "%s", "password": "%s" }
                """.formatted(email, password));
    }
}
