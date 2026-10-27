package br.com.contabilizape.centraldesk;

import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Atalhos dos testes de segurança das contas ({@link SecurityIntegrationTest}): cadastrar pelo endpoint,
 * ativar direto no banco quando o teste não é sobre a confirmação do e-mail, entrar e ler cookies.
 */
public final class TestAccounts {

    public static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    public static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    public static final String PASSWORD = "senhaForte123";

    private TestAccounts() {
    }

    /** Cadastra pelo endpoint (conta pendente de confirmação) e devolve o id. */
    public static UUID register(MockMvc mockMvc, JdbcTemplate jdbc, String email, String cpf, String name,
                                String departmentId) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "%s", "cpf": "%s", "email": "%s", "password": "%s", "departmentId": "%s" }
                        """.formatted(name, cpf, email, PASSWORD, departmentId)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    /** Conta ativa, com o e-mail confirmado; twoFactor diz se o login pede código. */
    public static UUID activeAccount(MockMvc mockMvc, JdbcTemplate jdbc, String email, String cpf, String name,
                                     boolean twoFactor) throws Exception {
        UUID id = register(mockMvc, jdbc, email, cpf, name, GENERAL_ID);
        jdbc.update("UPDATE users SET status = 'ACTIVE', email_verified_at = now(), two_factor_enabled = ? WHERE id = ?",
                twoFactor, id);
        return id;
    }

    /** Administrador ativo da Inovação (para administradores a TFA é obrigatória). */
    public static UUID activeAdmin(MockMvc mockMvc, JdbcTemplate jdbc, String email, String cpf, String name)
            throws Exception {
        UUID id = activeAccount(mockMvc, jdbc, email, cpf, name, true);
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, id);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", id);
        return id;
    }

    public static ResultActions login(MockMvc mockMvc, String email, String password) throws Exception {
        return login(mockMvc, email, password, null);
    }

    public static ResultActions login(MockMvc mockMvc, String email, String password, Cookie trustedDevice)
            throws Exception {
        var request = post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                { "email": "%s", "password": "%s" }
                """.formatted(email, password));
        if (trustedDevice != null) {
            request.cookie(trustedDevice);
        }
        return mockMvc.perform(request);
    }

    /**
     * Entra e devolve o cookie do access token. Se o login pedir o código (TFA), lê o código no e-mail
     * gravado pelo {@link RecordingEmailSender} e conclui o acesso.
     */
    public static Cookie signIn(MockMvc mockMvc, RecordingEmailSender emails, String email, String password)
            throws Exception {
        MockHttpServletResponse response = login(mockMvc, email, password).andReturn().getResponse();
        if (response.getStatus() == 202) {
            Cookie challenge = cookie(response, "tfa_challenge");
            response = mockMvc.perform(post("/auth/login/tfa").cookie(challenge)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"code\": \"%s\", \"trustThisBrowser\": false }".formatted(emails.lastCodeTo(email))))
                    .andReturn().getResponse();
        }
        if (response.getStatus() != 200) {
            throw new AssertionError("Login de " + email + " falhou: " + response.getStatus() + " " + response.getContentAsString());
        }
        return cookie(response, "access_token");
    }

    /** Cookie de nome {@code name} gravado pela resposta (Set-Cookie), ou null. */
    public static Cookie cookie(MockHttpServletResponse response, String name) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .map(header -> new Cookie(name, header.substring(header.indexOf('=') + 1, header.indexOf(';'))))
                .filter(cookie -> !cookie.getValue().isEmpty())
                .orElse(null);
    }

    /** Remove os administradores criados pelo teste (a limpeza padrão os preserva). */
    public static void removeAdmins(JdbcTemplate jdbc, String... emails) {
        for (String email : emails) {
            jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
        }
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }
}
