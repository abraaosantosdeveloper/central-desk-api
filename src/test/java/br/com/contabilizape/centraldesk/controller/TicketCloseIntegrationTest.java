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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Encerrar chamado (RF07): PATCH /tickets/{id}/close. Inovação, administradores e o próprio solicitante.
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
    void handlerClosesTicketInProgress() throws Exception {
        UUID ticket = ticket("IN_PROGRESS", handler);

        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(login(HANDLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedById").value(handler.toString()))
                .andExpect(jsonPath("$.closedAt").exists())
                .andExpect(jsonPath("$.assigneeId").value(handler.toString()));

        Map<String, Object> row = jdbc.queryForMap("SELECT status, closed_by_id, closed_at FROM tickets WHERE id = ?", ticket);
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("closed_by_id")).isEqualTo(handler);
        assertThat(row.get("closed_at")).isNotNull();
    }

    @Test
    void requesterClosesOwnOpenTicket() throws Exception {
        UUID ticket = ticket("OPEN", null);

        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(login(MARIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedById").value(maria.toString()));
    }

    @Test
    void adminClosesTicket() throws Exception {
        UUID ticket = ticket("OPEN", null);

        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void anotherCollaboratorGetsNotFound() throws Exception {
        UUID ticket = ticket("OPEN", null);
        register(JOAO, "11144477735");

        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(login(JOAO)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket)).isEqualTo("OPEN");
    }

    @Test
    void alreadyClosedTicketIsConflict() throws Exception {
        UUID ticket = ticket("CLOSED", handler);

        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(login(MARIA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Transição de status inválida"));
    }

    @Test
    void bodyIsIgnored() throws Exception {
        UUID ticket = ticket("IN_PROGRESS", handler);

        // Encerrar não recebe avaliação: ela é feita depois, em POST /tickets/{id}/rating
        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(login(MARIA))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"score\": 5 }"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ratings", Integer.class)).isZero();
    }

    @Test
    void unknownTicketIsNotFound() throws Exception {
        mockMvc.perform(patch("/tickets/" + UUID.randomUUID() + "/close").cookie(login(MARIA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/tickets/" + UUID.randomUUID() + "/close"))
                .andExpect(status().isUnauthorized());
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
