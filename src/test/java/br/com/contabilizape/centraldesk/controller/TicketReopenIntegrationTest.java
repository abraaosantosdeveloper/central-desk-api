package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
import br.com.contabilizape.centraldesk.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-208: reabertura de chamados (PLANO-SEGURANCA §13.2): o solicitante e os admins reabrem, em até
 * 7 dias, no máximo 3 vezes, com motivo; volta para o mesmo responsável ou para a fila.
 */
@IntegrationTest
class TicketReopenIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String JOAO = "joao.souza@contabilizape.com.br";
    private static final String CARLOS = "carlos.inovacao@contabilizape.com.br";
    private static final String ANA = "ana.admin@contabilizape.com.br";
    private static final String REASON = "A impressora voltou a falhar depois de uma hora.";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingEmailSender emails;

    private UUID maria;
    private UUID carlos;
    private UUID ana;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        maria = register(MARIA, "52998224725", "Maria da Silva");
        register(JOAO, "11144477735", "João Souza");
        carlos = register(CARLOS, "39053344705", "Carlos Lima");
        ana = register(ANA, "71428793860", "Ana Admin");
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id IN (?, ?)", INNOVATION_ID, carlos, ana);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", ana);
        emails.clear();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ANA);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void requesterReopensAndTheTicketGoesBackToTheSameAssignee() throws Exception {
        UUID ticket = closedTicket(carlos, 1);

        reopen(ticket, MARIA, REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assigneeId").value(carlos.toString()))
                .andExpect(jsonPath("$.closedAt").doesNotExist())
                .andExpect(jsonPath("$.resolution").doesNotExist());

        // O encerramento anterior fica no histórico; o motivo entra na conversa
        Map<String, Object> history = jdbc.queryForMap(
                "SELECT resolution, reopened_by_id, reopen_reason FROM ticket_closures WHERE ticket_id = ?", ticket);
        assertThat(history.get("resolution")).isEqualTo("Troquei a fonte.");
        assertThat(history.get("reopened_by_id")).isEqualTo(maria);
        assertThat(history.get("reopen_reason")).isEqualTo(REASON);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE ticket_id = ?", Integer.class, ticket)).isEqualTo(1);

        assertThat(emails.subjectsTo(CARLOS)).containsExactly("Chamado reaberto");
        assertThat(emails.to(CARLOS).getFirst().html()).contains(REASON).contains("O atendimento continua com");
        assertThat(emails.subjectsTo(MARIA)).isEmpty();
    }

    @Test
    void adminReopensAndTheRequesterIsWarned() throws Exception {
        UUID ticket = closedTicket(carlos, 1);

        reopen(ticket, ANA, REASON).andExpect(status().isOk());

        assertThat(emails.subjectsTo(CARLOS)).containsExactly("Chamado reaberto");
        assertThat(emails.subjectsTo(MARIA)).containsExactly("Chamado reaberto");
        assertThat(emails.subjectsTo(ANA)).isEmpty();
    }

    @Test
    void assigneeWhoLeftSendsTheTicketBackToTheQueue() throws Exception {
        UUID ticket = closedTicket(carlos, 1);
        jdbc.update("UPDATE users SET status = 'DEACTIVATED', deactivated_at = now() WHERE id = ?", carlos);

        reopen(ticket, MARIA, REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assigneeId").doesNotExist());

        // Na fila: quem é avisado são os administradores do setor
        assertThat(emails.subjectsTo(ANA)).containsExactly("Chamado reaberto");
        assertThat(emails.to(ANA).getFirst().html()).contains("voltou para a fila");
    }

    @Test
    void assigneeWhoLeftInnovationAlsoSendsItToTheQueue() throws Exception {
        UUID ticket = closedTicket(carlos, 1);
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", GENERAL_ID, carlos);

        reopen(ticket, MARIA, REASON).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void onlyTheRequesterAndAdminsReopen() throws Exception {
        UUID ticket = closedTicket(carlos, 1);

        reopen(ticket, CARLOS, REASON).andExpect(status().isForbidden());
        // Quem não vê o chamado não descobre que ele existe
        reopen(ticket, JOAO, REASON).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket)).isEqualTo("CLOSED");
    }

    @Test
    void reopeningHasADeadlineALimitAndOnlyAppliesToClosedTickets() throws Exception {
        UUID old = closedTicket(carlos, 8);
        reopen(old, MARIA, REASON).andExpect(status().isConflict());

        UUID ticket = closedTicket(carlos, 1);
        for (int i = 0; i < 3; i++) {
            reopen(ticket, MARIA, REASON).andExpect(status().isOk());
            reopen(ticket, MARIA, REASON).andExpect(status().isConflict()); // não está encerrado
            close(ticket);
        }
        reopen(ticket, MARIA, REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este chamado já foi reaberto 3 vezes. Abra um chamado novo."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_closures WHERE ticket_id = ?", Integer.class, ticket))
                .isEqualTo(3);
    }

    @Test
    void reasonIsRequired() throws Exception {
        UUID ticket = closedTicket(carlos, 1);

        reopen(ticket, MARIA, "curto").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        reopen(ticket, MARIA, "x".repeat(1001)).andExpect(status().isBadRequest());
    }

    // ---------- auxiliares ----------

    private UUID closedTicket(UUID assignee, int closedDaysAgo) {
        UUID category = jdbc.queryForObject("SELECT id FROM categories WHERE archived_at IS NULL LIMIT 1", UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id,
                                     assigned_at, closed_by_id, closed_at, resolution)
                VALUES ('Impressora', 'Sem papel', 'CLOSED', ?, 2, ?, ?, now() - interval '10 days', ?,
                        now() - make_interval(days => ?::int), 'Troquei a fonte.')
                RETURNING id""", UUID.class, category, maria, assignee, assignee, closedDaysAgo);
    }

    private void close(UUID ticket) throws Exception {
        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(login(CARLOS))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"resolution\": \"Troquei de novo.\" }"))
                .andExpect(status().isOk());
    }

    private ResultActions reopen(UUID ticket, String actor, String reason) throws Exception {
        return mockMvc.perform(patch("/tickets/" + ticket + "/reopen").cookie(login(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"reason\": \"%s\" }".formatted(reason)));
    }

    private UUID register(String email, String cpf, String name) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "%s", "cpf": "%s", "email": "%s", "password": "%s", "departmentId": "%s" }
                        """.formatted(name, cpf, email, PASSWORD, GENERAL_ID)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private Cookie login(String email) throws Exception {
        String header = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("access_token=")).findFirst().orElseThrow();
        return new Cookie("access_token", header.substring(header.indexOf('=') + 1, header.indexOf(';')));
    }
}
