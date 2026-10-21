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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comentários (RF13): POST e GET /tickets/{id}/comments. O conteúdo é gravado cifrado (AES-256-GCM).
 */
@IntegrationTest
class CommentIntegrationTest {

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
    private Cookie mariaSession;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        maria = register(MARIA, "52998224725", "Maria da Silva");
        handler = register(HANDLER, "39053344705", "Carlos Inovação");
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", UUID.fromString(INNOVATION_ID), handler);
        mariaSession = login(MARIA);
    }

    @Test
    void requesterCommentsAndOnlyTheCipherIsStored() throws Exception {
        UUID ticket = ticket("OPEN", null);

        mockMvc.perform(comment(ticket, "A senha do wifi do 2º andar é Contab1liza").cookie(mariaSession))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("A senha do wifi do 2º andar é Contab1liza"))
                .andExpect(jsonPath("$.authorId").value(maria.toString()))
                .andExpect(jsonPath("$.authorName").value("Maria da Silva"))
                .andExpect(jsonPath("$.ticketId").value(ticket.toString()))
                .andExpect(jsonPath("$.createdAt").exists());

        String stored = jdbc.queryForObject("SELECT content FROM comments WHERE ticket_id = ?", String.class, ticket);
        assertThat(stored).startsWith("v1:").doesNotContain("wifi").doesNotContain("Contab1liza");
    }

    @Test
    void conversationIsReadInChronologicalOrder() throws Exception {
        UUID ticket = ticket("IN_PROGRESS", handler);
        Cookie handlerSession = login(HANDLER);

        mockMvc.perform(comment(ticket, "Não liga de jeito nenhum").cookie(mariaSession)).andExpect(status().isCreated());
        mockMvc.perform(comment(ticket, "Vou trocar a fonte").cookie(handlerSession)).andExpect(status().isCreated());

        mockMvc.perform(get("/tickets/" + ticket + "/comments").cookie(mariaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].content", contains("Não liga de jeito nenhum", "Vou trocar a fonte")))
                .andExpect(jsonPath("$[*].authorName", contains("Maria da Silva", "Carlos Inovação")));
    }

    @Test
    void handlerCanCommentOnAnyTicket() throws Exception {
        UUID ticket = ticket("OPEN", null);

        mockMvc.perform(comment(ticket, "Pode mandar uma foto do erro?").cookie(login(HANDLER)))
                .andExpect(status().isCreated());
    }

    @Test
    void anotherCollaboratorGetsNotFound() throws Exception {
        UUID ticket = ticket("OPEN", null);
        register(JOAO, "11144477735", "João Souza");
        Cookie joao = login(JOAO);

        mockMvc.perform(comment(ticket, "Intrometido").cookie(joao)).andExpect(status().isNotFound());
        mockMvc.perform(get("/tickets/" + ticket + "/comments").cookie(joao)).andExpect(status().isNotFound());
    }

    @Test
    void closedTicketRejectsNewCommentsButKeepsHistory() throws Exception {
        UUID ticket = ticket("IN_PROGRESS", handler);
        mockMvc.perform(comment(ticket, "Resolvido?").cookie(mariaSession)).andExpect(status().isCreated());
        jdbc.update("UPDATE tickets SET status = 'CLOSED', closed_at = now() WHERE id = ?", ticket);

        mockMvc.perform(comment(ticket, "Mais uma coisa").cookie(mariaSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Chamado encerrado"));
        mockMvc.perform(get("/tickets/" + ticket + "/comments").cookie(mariaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].content", contains("Resolvido?")));
    }

    @Test
    void blankOrTooLongCommentIsBadRequest() throws Exception {
        UUID ticket = ticket("OPEN", null);

        for (String content : new String[] {"   ", "a".repeat(5001)}) {
            mockMvc.perform(comment(ticket, content).cookie(mariaSession))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.content").exists());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comments", Integer.class)).isZero();
    }

    @Test
    void unknownTicketIsNotFound() throws Exception {
        mockMvc.perform(comment(UUID.randomUUID(), "Oi").cookie(mariaSession)).andExpect(status().isNotFound());
    }

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        UUID ticket = ticket("OPEN", null);

        mockMvc.perform(comment(ticket, "Oi")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/tickets/" + ticket + "/comments")).andExpect(status().isUnauthorized());
    }

    // ---------- auxiliares ----------

    private static MockHttpServletRequestBuilder comment(UUID ticket, String content) {
        return post("/tickets/" + ticket + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{ \"content\": \"%s\" }".formatted(content));
    }

    private UUID register(String email, String cpf, String name) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "%s", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
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

    private UUID ticket(String status, UUID assignee) {
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id)
                VALUES ('Impressora', 'Sem papel', ?, ?, 2, ?, ?) RETURNING id
                """, UUID.class, status, category, maria, assignee);
    }
}
