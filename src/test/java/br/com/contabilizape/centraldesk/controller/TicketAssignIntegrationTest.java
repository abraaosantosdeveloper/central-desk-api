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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Assumir chamado (RF06): PATCH /tickets/{id}/assign. OPEN → IN_PROGRESS, só Inovação e administradores.
 */
@IntegrationTest
class TicketAssignIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
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
    void handlerAssignsOpenTicket() throws Exception {
        UUID ticket = ticket("OPEN", null);

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(login(HANDLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assigneeId").value(handler.toString()))
                .andExpect(jsonPath("$.assignedAt").exists());

        Map<String, Object> row = jdbc.queryForMap("SELECT status, assignee_id, assigned_at FROM tickets WHERE id = ?", ticket);
        assertThat(row.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(row.get("assignee_id")).isEqualTo(handler);
        assertThat(row.get("assigned_at")).isNotNull();
    }

    @Test
    void assignedTicketLeavesTheQueueAndAppearsInAssigned() throws Exception {
        UUID ticket = ticket("OPEN", null);
        Cookie session = login(HANDLER);

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(session)).andExpect(status().isOk());

        mockMvc.perform(get("/tickets/queue").cookie(session))
                .andExpect(jsonPath("$.content", empty()));
        mockMvc.perform(get("/tickets/assigned").cookie(session))
                .andExpect(jsonPath("$.content[*].id", contains(ticket.toString())));
    }

    @Test
    void adminAssignsOpenTicket() throws Exception {
        UUID ticket = ticket("OPEN", null);

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void ticketAlreadyInProgressIsConflict() throws Exception {
        UUID other = register("bia.inovacao@contabilizape.com.br", "86288366757");
        UUID ticket = ticket("IN_PROGRESS", other);

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(login(HANDLER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Transição de status inválida"));
        assertThat(jdbc.queryForObject("SELECT assignee_id FROM tickets WHERE id = ?", UUID.class, ticket)).isEqualTo(other);
    }

    @Test
    void closedTicketIsConflict() throws Exception {
        UUID ticket = ticket("CLOSED", null);

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(login(HANDLER)))
                .andExpect(status().isConflict());
    }

    @Test
    void collaboratorCannotAssign() throws Exception {
        UUID ticket = ticket("OPEN", null);

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(login(MARIA)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket)).isEqualTo("OPEN");
    }

    @Test
    void unknownTicketIsNotFound() throws Exception {
        mockMvc.perform(patch("/tickets/" + UUID.randomUUID() + "/assign").cookie(login(HANDLER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(patch("/tickets/" + UUID.randomUUID() + "/assign"))
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
