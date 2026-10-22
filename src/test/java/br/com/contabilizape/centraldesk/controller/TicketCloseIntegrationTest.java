package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
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
 * Encerrar chamado (RF07): PATCH /tickets/{id}/close. Inovação e administradores; o solicitante não encerra o próprio chamado.
 */
@IntegrationTest
class TicketCloseIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String JOAO = "joao.souza@contabilizape.com.br";
    private static final String HANDLER = "carlos.inovacao@contabilizape.com.br";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";

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
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", UUID.fromString(INNOVATION_ID), handler);
    }

    // A limpeza padrão preserva administradores; o criado aqui sai ao final
    @AfterEach
    void removeTestAdmin() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void handlerClosesTicketInProgressWithWhatWasDone() throws Exception {
        UUID ticket = ticket("IN_PROGRESS", handler);

        close(ticket, login(HANDLER), "  Troquei a fonte do notebook e testei com o usuário.  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedById").value(handler.toString()))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.resolution").value("Troquei a fonte do notebook e testei com o usuário."))
                .andExpect(jsonPath("$.assigneeId").value(handler.toString()));

        Map<String, Object> row = jdbc.queryForMap("SELECT status, closed_by_id, closed_at, resolution FROM tickets WHERE id = ?", ticket);
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("closed_by_id")).isEqualTo(handler);
        assertThat(row.get("closed_at")).isNotNull();
        assertThat(row.get("resolution")).isEqualTo("Troquei a fonte do notebook e testei com o usuário.");
    }

    @Test
    void whatWasDoneIsRequired() throws Exception {
        UUID ticket = ticket("IN_PROGRESS", handler);
        Cookie session = login(HANDLER);

        close(ticket, session, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.resolution").exists());
        close(ticket, session, "x".repeat(2001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.resolution").exists());
        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(session))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void requesterCannotCloseOwnTicket() throws Exception {
        UUID ticket = ticket("IN_PROGRESS", handler);

        // Quem encerra é quem atende: o solicitante pede o encerramento pela conversa
        close(ticket, login(MARIA), "Resolvido").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void adminClosesTicket() throws Exception {
        UUID ticket = ticket("OPEN", null);

        close(ticket, adminSession(), "Chamado duplicado do #1A2B3C4D.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void anotherCollaboratorGetsNotFound() throws Exception {
        UUID ticket = ticket("OPEN", null);
        register(JOAO, "11144477735");

        close(ticket, login(JOAO), "Intrometido").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket)).isEqualTo("OPEN");
    }

    @Test
    void alreadyClosedTicketIsConflict() throws Exception {
        UUID ticket = ticket("CLOSED", handler);

        close(ticket, login(HANDLER), "De novo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Transição de status inválida"));
    }

    @Test
    void ratingIsNotPartOfTheClosing() throws Exception {
        UUID ticket = ticket("IN_PROGRESS", handler);

        // Encerrar não recebe avaliação: ela é feita depois, em POST /tickets/{id}/rating
        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(login(HANDLER))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"resolution\": \"Feito\", \"score\": 5 }"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ratings", Integer.class)).isZero();
    }

    @Test
    void unknownTicketIsNotFound() throws Exception {
        close(UUID.randomUUID(), login(HANDLER), "Feito").andExpect(status().isNotFound());
    }

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/tickets/" + UUID.randomUUID() + "/close")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"resolution\": \"Feito\" }"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions close(UUID ticket, Cookie session, String resolution) throws Exception {
        return mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"resolution\": \"%s\" }".formatted(resolution)));
    }

    // ---------- auxiliares ----------

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

    private Cookie adminSession() throws Exception {
        UUID admin = register(ADMIN, "71428793860");
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        return login(ADMIN);
    }

    private UUID ticket(String status, UUID assignee) {
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id)
                VALUES ('Impressora', 'Sem papel', ?, ?, 2, ?, ?) RETURNING id
                """, UUID.class, status, category, maria, assignee);
    }
}
