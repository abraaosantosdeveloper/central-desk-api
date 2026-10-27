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
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static br.com.contabilizape.centraldesk.TestAccounts.PASSWORD;
import static br.com.contabilizape.centraldesk.TestAccounts.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-202: bloqueio depois de 5 tentativas erradas seguidas (PLANO-SEGURANCA §5), auditoria e limite por IP.
 */
@SecurityIntegrationTest
class LoginLockoutIntegrationTest {

    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String WRONG = "senhaErrada999";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingEmailSender emails;
    @Autowired private LoginAttemptLimiter limiter;

    private UUID maria;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        limiter.clear();
        maria = TestAccounts.activeAccount(mockMvc, jdbc, MARIA, "52998224725", "Maria da Silva", false);
        TestAccounts.activeAdmin(mockMvc, jdbc, ADMIN, "71428793860", "Ana Admin");
        emails.clear();
    }

    @AfterEach
    void cleanUp() {
        limiter.clear();
        TestAccounts.removeAdmins(jdbc, ADMIN);
    }

    @Test
    void fourMistakesAndThenTheRightPasswordStartsTheCountAgain() throws Exception {
        for (int i = 0; i < 4; i++) {
            login(mockMvc, MARIA, WRONG).andExpect(status().isUnauthorized());
        }
        assertThat(attempts()).isEqualTo(4);

        login(mockMvc, MARIA, PASSWORD).andExpect(status().isOk());

        assertThat(attempts()).isZero();
        assertThat(accountStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void fifthMistakeBlocksTheAccountEndsItsSessionsAndWarnsOwnerAndAdmins() throws Exception {
        Cookie session = TestAccounts.cookie(login(mockMvc, MARIA, PASSWORD).andReturn().getResponse(), "access_token");

        for (int i = 0; i < 4; i++) {
            login(mockMvc, MARIA, WRONG).andExpect(status().isUnauthorized());
        }
        // A 5ª resposta é o mesmo 401 de sempre: quem ataca não fica sabendo do bloqueio por ela
        login(mockMvc, MARIA, WRONG)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));

        assertThat(accountStatus()).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject("SELECT blocked_at FROM users WHERE id = ?", Object.class, maria)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, maria)).isZero();
        // A sessão aberta caiu (401 pelo corte de sessões, ou 403 se o token é do mesmo segundo do corte)
        mockMvc.perform(get("/users/me").cookie(session))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));

        assertThat(emails.subjectsTo(MARIA)).containsExactly("Sua conta foi bloqueada");
        assertThat(emails.subjectsTo(ADMIN)).containsExactly("Uma conta foi bloqueada no CentralDesk");
        assertThat(emails.to(ADMIN).getFirst().html()).contains("Maria da Silva").contains(MARIA);
        assertThat(events("ACCOUNT_BLOCKED")).isEqualTo(1);
        assertThat(events("LOGIN_FAILED")).isEqualTo(5);
    }

    @Test
    void rightPasswordOnABlockedAccountSaysItIsBlockedButOpensNothing() throws Exception {
        jdbc.update("UPDATE users SET status = 'BLOCKED', blocked_at = now() WHERE id = ?", maria);

        MvcResult result = login(mockMvc, MARIA, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account-blocked"))
                .andExpect(jsonPath("$.detail").value("Conta bloqueada. Procure a equipe de Inovação."))
                .andReturn();

        assertThat(TestAccounts.cookie(result.getResponse(), "access_token")).isNull();
        assertThat(TestAccounts.cookie(result.getResponse(), "refresh_token")).isNull();
    }

    @Test
    void wrongPasswordOnABlockedAccountLooksExactlyLikeAnUnknownEmail() throws Exception {
        jdbc.update("UPDATE users SET status = 'BLOCKED', blocked_at = now() WHERE id = ?", maria);

        String blocked = login(mockMvc, MARIA, WRONG).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = login(mockMvc, "ninguem@contabilizape.com.br", WRONG).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(blocked).isEqualTo(unknown);
        // Conta bloqueada não acumula mais falhas
        assertThat(attempts()).isZero();
    }

    @Test
    void simultaneousMistakesBlockTheAccountOnlyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(7);
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            attempts.add(() -> login(mockMvc, MARIA, WRONG).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : pool.invokeAll(attempts)) {
            statuses.add(future.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnly(401);
        assertThat(attempts()).isEqualTo(5);
        assertThat(events("ACCOUNT_BLOCKED")).isEqualTo(1);
        assertThat(emails.subjectsTo(MARIA)).containsExactly("Sua conta foi bloqueada");
    }

    @Test
    void tooManyMistakesFromTheSameIpAreRefusedForAWhile() throws Exception {
        // 20 falhas do mesmo IP, espalhadas por contas que não existem (ninguém é bloqueado)
        for (int i = 0; i < 20; i++) {
            fromIp("ninguem" + i + "@contabilizape.com.br", WRONG, "203.0.113.7").andExpect(status().isUnauthorized());
        }

        fromIp(MARIA, PASSWORD, "203.0.113.7")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // Outro IP continua entrando
        fromIp(MARIA, PASSWORD, "198.51.100.4").andExpect(status().isOk());
    }

    @Test
    void auditTrailKeepsTheIpOfTheAttempt() throws Exception {
        fromIp(MARIA, WRONG, "203.0.113.9").andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT ip FROM security_events WHERE user_id = ? AND type = 'LOGIN_FAILED'",
                String.class, maria)).isEqualTo("203.0.113.9");
    }

    // ---------- auxiliares ----------

    private org.springframework.test.web.servlet.ResultActions fromIp(String email, String password, String ip)
            throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .content("{ \"email\": \"%s\", \"password\": \"%s\" }".formatted(email, password)));
    }

    private int attempts() {
        return jdbc.queryForObject("SELECT failed_login_attempts FROM users WHERE id = ?", Integer.class, maria);
    }

    private String accountStatus() {
        return jdbc.queryForObject("SELECT status FROM users WHERE id = ?", String.class, maria);
    }

    private int events(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM security_events WHERE user_id = ? AND type = ?",
                Integer.class, maria, type);
    }
}
