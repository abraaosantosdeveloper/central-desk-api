package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.RecordingEmailSender;
import br.com.contabilizape.centraldesk.SecurityIntegrationTest;
import br.com.contabilizape.centraldesk.TestAccounts;
import br.com.contabilizape.centraldesk.job.AccountMaintenanceJob;
import br.com.contabilizape.centraldesk.security.LoginAttemptLimiter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static br.com.contabilizape.centraldesk.TestAccounts.GENERAL_ID;
import static br.com.contabilizape.centraldesk.TestAccounts.INNOVATION_ID;
import static br.com.contabilizape.centraldesk.TestAccounts.PASSWORD;
import static br.com.contabilizape.centraldesk.TestAccounts.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-205: confirmação do e-mail no cadastro (PLANO-SEGURANCA §8 e B3).
 */
@SecurityIntegrationTest
class EmailVerificationIntegrationTest {

    private static final String MARIA = "maria.silva@contabilizape.com.br";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingEmailSender emails;
    @Autowired private LoginAttemptLimiter limiter;
    @Autowired private AccountMaintenanceJob maintenance;

    @BeforeEach
    void setUp() {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        limiter.clear();
        emails.clear();
    }

    @AfterEach
    void cleanUp() {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void registrationSendsTheCodeAndOpensNoSession() throws Exception {
        MockHttpServletResponse response = register(MARIA, "52998224725", "Maria da Silva", PASSWORD, GENERAL_ID)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andReturn().getResponse();

        assertThat(TestAccounts.cookie(response, "access_token")).isNull();
        assertThat(emails.subjectsTo(MARIA)).containsExactly("Confirme seu e-mail");
        assertThat(emails.lastCodeTo(MARIA)).matches("\\d{6}");
    }

    @Test
    void loginBeforeConfirmingIsRefused() throws Exception {
        register(MARIA, "52998224725", "Maria da Silva", PASSWORD, GENERAL_ID).andExpect(status().isCreated());

        login(mockMvc, MARIA, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("email-not-verified"));
        login(mockMvc, MARIA, "senhaErrada1").andExpect(status().isUnauthorized());
    }

    @Test
    void confirmingActivatesTheAccountAndOpensTheSession() throws Exception {
        UUID maria = registered(MARIA, GENERAL_ID);

        MockHttpServletResponse response = verify(MARIA, emails.lastCodeTo(MARIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(MARIA))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse();

        Cookie session = TestAccounts.cookie(response, "access_token");
        mockMvc.perform(get("/tickets/mine").cookie(session)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT email_verified_at FROM users WHERE id = ?", Object.class, maria)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_events WHERE user_id = ? AND type = 'EMAIL_VERIFIED'",
                Integer.class, maria)).isEqualTo(1);
    }

    @Test
    void innovationRequestIsCreatedOnlyAfterTheConfirmation() throws Exception {
        UUID maria = registered(MARIA, INNOVATION_ID);
        assertThat(requestsOf(maria)).isZero();

        verify(MARIA, emails.lastCodeTo(MARIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentApprovalPending").value(true));

        assertThat(requestsOf(maria)).isEqualTo(1);
    }

    @Test
    void wrongCodeOrUnknownEmailGetTheSameAnswer() throws Exception {
        registered(MARIA, GENERAL_ID);

        verify(MARIA, "000000").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Código inválido ou vencido."));
        verify("ninguem@contabilizape.com.br", "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("Código inválido ou vencido."));
    }

    @Test
    void newRegistrationReplacesThePendingOne() throws Exception {
        registered(MARIA, GENERAL_ID);
        String firstCode = emails.lastCodeTo(MARIA);
        // A tentativa seguinte precisa respeitar o intervalo de 60 s entre códigos
        jdbc.update("UPDATE verification_codes SET created_at = created_at - interval '2 minutes'");

        register(MARIA, "52998224725", "Maria Souza", "outraSenha77", GENERAL_ID).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, MARIA)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT full_name FROM users WHERE email = ?", String.class, MARIA)).isEqualTo("Maria Souza");
        String secondCode = emails.lastCodeTo(MARIA);
        if (!secondCode.equals(firstCode)) {
            verify(MARIA, firstCode).andExpect(status().isBadRequest());
        }
        verify(MARIA, secondCode).andExpect(status().isOk());
        login(mockMvc, MARIA, "outraSenha77").andExpect(status().isOk());
    }

    @Test
    void confirmedAccountsKeepTheirEmailAndCpf() throws Exception {
        TestAccounts.activeAccount(mockMvc, jdbc, MARIA, "52998224725", "Maria da Silva", false);

        register(MARIA, "11144477735", "Outra Pessoa", PASSWORD, GENERAL_ID).andExpect(status().isConflict());
        register("outra@contabilizape.com.br", "52998224725", "Outra Pessoa", PASSWORD, GENERAL_ID).andExpect(status().isConflict());
    }

    @Test
    void resendAlwaysAnswersTheSame() throws Exception {
        registered(MARIA, GENERAL_ID);
        jdbc.update("UPDATE verification_codes SET created_at = created_at - interval '2 minutes'");

        String known = resend(MARIA).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknown = resend("ninguem@contabilizape.com.br").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(known).isEqualTo(unknown);
        assertThat(emails.subjectsTo(MARIA)).hasSize(2);
    }

    @Test
    void unconfirmedRegistrationsAreErasedAfter24Hours() throws Exception {
        UUID old = registered(MARIA, GENERAL_ID);
        UUID fresh = registered("joao.souza@contabilizape.com.br", GENERAL_ID, "11144477735");
        jdbc.update("UPDATE users SET created_at = now() - interval '25 hours' WHERE id = ?", old);

        maintenance.run();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, old)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, fresh)).isEqualTo(1);
    }

    // ---------- auxiliares ----------

    private UUID registered(String email, String departmentId) throws Exception {
        return registered(email, departmentId, "52998224725");
    }

    private UUID registered(String email, String departmentId, String cpf) throws Exception {
        register(email, cpf, "Maria da Silva", PASSWORD, departmentId).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private ResultActions register(String email, String cpf, String name, String password, String departmentId)
            throws Exception {
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                { "fullName": "%s", "cpf": "%s", "email": "%s", "password": "%s", "departmentId": "%s" }
                """.formatted(name, cpf, email, password, departmentId)));
    }

    private ResultActions verify(String email, String code) throws Exception {
        return mockMvc.perform(post("/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\", \"code\": \"%s\" }".formatted(email, code)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/auth/email/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"email\": \"%s\" }".formatted(email)));
    }

    private int requestsOf(UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM department_requests WHERE user_id = ?", Integer.class, userId);
    }
}
