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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stream SSE por usuário (GET /events/stream): cada um recebe só os eventos que lhe dizem respeito.
 */
@IntegrationTest
class RealtimeIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String JOAO = "joao.souza@contabilizape.com.br";
    private static final String HANDLER = "carlos.inovacao@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID maria;
    private UUID handler;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        maria = register(MARIA, "52998224725");
        handler = register(HANDLER, "39053344705");
        register(JOAO, "11144477735");
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", UUID.fromString(INNOVATION_ID), handler);
    }

    @Test
    void streamOpensAsServerSentEvents() throws Exception {
        MockHttpServletResponse stream = openStream(login(MARIA));

        assertThat(stream.getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @Test
    void streamWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/events/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void newCommentReachesTheTicketParticipantsOnly() throws Exception {
        UUID ticket = ticket(handler);
        Cookie mariaSession = login(MARIA);
        Cookie handlerSession = login(HANDLER);
        MockHttpServletResponse mariaStream = openStream(mariaSession);
        MockHttpServletResponse joaoStream = openStream(login(JOAO));

        mockMvc.perform(post("/tickets/" + ticket + "/comments").cookie(handlerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"content\": \"Chego em 5 minutos\" }"))
                .andExpect(status().isCreated());

        assertThat(mariaStream.getContentAsString())
                .contains("event:comment.created")
                .contains("Chego em 5 minutos")
                .contains(ticket.toString());
        assertThat(joaoStream.getContentAsString()).doesNotContain("comment.created");
    }

    @Test
    void newTicketReachesInnovationButNotOtherCollaborators() throws Exception {
        MockHttpServletResponse handlerStream = openStream(login(HANDLER));
        MockHttpServletResponse joaoStream = openStream(login(JOAO));
        String category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", String.class);

        mockMvc.perform(post("/tickets").cookie(login(MARIA)).contentType(MediaType.APPLICATION_JSON).content("""
                        { "title": "Monitor piscando", "description": "Desde ontem.",
                          "categoryId": "%s", "priorityId": 2 }
                        """.formatted(category)))
                .andExpect(status().isCreated());

        assertThat(handlerStream.getContentAsString())
                .contains("event:ticket.created")
                .contains("Monitor piscando")
                .doesNotContain("Desde ontem."); // vai o resumo, sem a descrição
        assertThat(joaoStream.getContentAsString()).doesNotContain("ticket.created");
    }

    // ---------- auxiliares ----------

    private MockHttpServletResponse openStream(Cookie session) throws Exception {
        return mockMvc.perform(get("/events/stream").cookie(session).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
    }

    private UUID register(String email, String cpf) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Maria da Silva", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(cpf, email, PASSWORD, GENERAL_ID)))
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

    private UUID ticket(UUID assignee) {
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id)
                VALUES ('Impressora', 'Sem papel', 'IN_PROGRESS', ?, 2, ?, ?) RETURNING id
                """, UUID.class, category, maria, assignee);
    }
}
