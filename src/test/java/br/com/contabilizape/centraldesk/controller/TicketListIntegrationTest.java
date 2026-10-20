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

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Listagens dos painéis (RF11): GET /tickets/mine, /tickets/queue e /tickets/assigned.
 * Paginadas, mais recentes primeiro, com filtros opcionais e resumo sem a descrição.
 */
@IntegrationTest
class TicketListIntegrationTest {

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
    private UUID joao;
    private UUID handler;
    private Cookie mariaSession;
    private UUID hardware;
    private UUID software;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        maria = register(MARIA, "52998224725");
        joao = register(JOAO, "11144477735");
        handler = register(HANDLER, "39053344705");
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", UUID.fromString(INNOVATION_ID), handler);
        mariaSession = login(MARIA);
        hardware = jdbc.queryForObject("SELECT id FROM categories WHERE label = 'Hardware'", UUID.class);
        software = jdbc.queryForObject("SELECT id FROM categories WHERE label <> 'Hardware' ORDER BY label LIMIT 1", UUID.class);
    }

    // A limpeza padrão preserva administradores; o criado aqui sai ao final
    @AfterEach
    void removeTestAdmin() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // ---------- GET /tickets/mine ----------

    @Test
    void mineListsOnlyMyTicketsNewestFirstWithoutDescription() throws Exception {
        ticket("Antigo", maria, "OPEN", null, hardware, 2, 3);
        ticket("Novo", maria, "IN_PROGRESS", handler, hardware, 2, 1);
        ticket("Do João", joao, "OPEN", null, hardware, 2, 0);

        mockMvc.perform(get("/tickets/mine").cookie(mariaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Novo", "Antigo")))
                .andExpect(jsonPath("$.content[0].description").doesNotExist())
                .andExpect(jsonPath("$.content[0].assigneeId").value(handler.toString()))
                .andExpect(jsonPath("$.content[0].category.label").value("Hardware"))
                .andExpect(jsonPath("$.content[0].priority.id").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void mineIsPaginated() throws Exception {
        ticket("Primeiro", maria, "OPEN", null, hardware, 2, 3);
        ticket("Segundo", maria, "OPEN", null, hardware, 2, 2);
        ticket("Terceiro", maria, "OPEN", null, hardware, 2, 1);

        mockMvc.perform(get("/tickets/mine").param("page", "1").param("size", "2").cookie(mariaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Primeiro")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void mineAppliesTheOptionalFilters() throws Exception {
        ticket("Aberto hardware média", maria, "OPEN", null, hardware, 2, 4);
        ticket("Em atendimento", maria, "IN_PROGRESS", handler, hardware, 2, 3);
        ticket("Outra categoria", maria, "OPEN", null, software, 2, 2);
        ticket("Urgente", maria, "OPEN", null, hardware, 4, 1);

        mockMvc.perform(get("/tickets/mine").param("status", "OPEN").param("categoryId", hardware.toString())
                        .param("priorityId", "2").cookie(mariaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Aberto hardware média")));
    }

    @Test
    void invalidStatusFilterIsBadRequest() throws Exception {
        mockMvc.perform(get("/tickets/mine").param("status", "PERDIDO").cookie(mariaSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mineWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/tickets/mine")).andExpect(status().isUnauthorized());
    }

    // ---------- GET /tickets/queue ----------

    @Test
    void queueShowsOpenTicketsOfEveryoneToTheHandler() throws Exception {
        ticket("Da Maria", maria, "OPEN", null, hardware, 2, 2);
        ticket("Do João", joao, "OPEN", null, hardware, 2, 1);
        ticket("Já assumido", joao, "IN_PROGRESS", handler, hardware, 2, 0);

        mockMvc.perform(get("/tickets/queue").cookie(login(HANDLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Do João", "Da Maria")));
    }

    @Test
    void adminSeesTheQueue() throws Exception {
        ticket("Da Maria", maria, "OPEN", null, hardware, 2, 1);

        mockMvc.perform(get("/tickets/queue").cookie(adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void collaboratorCannotSeeTheQueue() throws Exception {
        mockMvc.perform(get("/tickets/queue").cookie(mariaSession)).andExpect(status().isForbidden());
    }

    // ---------- GET /tickets/assigned ----------

    @Test
    void assignedListsTicketsOfTheLoggedHandler() throws Exception {
        UUID otherHandler = register("bia.inovacao@contabilizape.com.br", "86288366757");
        ticket("Comigo", maria, "IN_PROGRESS", handler, hardware, 2, 2);
        ticket("Encerrado comigo", joao, "CLOSED", handler, hardware, 2, 1);
        ticket("Com outra pessoa", maria, "IN_PROGRESS", otherHandler, hardware, 2, 0);
        ticket("Na fila", maria, "OPEN", null, hardware, 2, 0);

        mockMvc.perform(get("/tickets/assigned").cookie(login(HANDLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", containsInAnyOrder("Comigo", "Encerrado comigo")));

        mockMvc.perform(get("/tickets/assigned").param("status", "IN_PROGRESS").cookie(login(HANDLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("Comigo")));
    }

    @Test
    void collaboratorCannotUseAssigned() throws Exception {
        mockMvc.perform(get("/tickets/assigned").cookie(mariaSession)).andExpect(status().isForbidden());
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

    // created_at controlado (horas atrás) para a ordem não depender da velocidade do teste
    private void ticket(String title, UUID requester, String status, UUID assignee, UUID category, int priority, int hoursAgo) {
        jdbc.update("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id, created_at)
                VALUES (?, 'Descrição', ?, ?, ?, ?, ?, now() - make_interval(hours => ?))
                """, title, status, category, priority, requester, assignee, hoursAgo);
    }
}
