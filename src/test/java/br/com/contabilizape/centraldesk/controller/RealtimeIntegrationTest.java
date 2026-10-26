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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String PAULO = "paulo.novo@contabilizape.com.br";
    private static final String COLLEAGUE = "dani.inovacao@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID maria;
    private UUID handler;
    private UUID admin;
    private Cookie adminSession;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        maria = register(MARIA, "52998224725");
        handler = register(HANDLER, "39053344705");
        register(JOAO, "11144477735");
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", UUID.fromString(INNOVATION_ID), handler);
        admin = register(ADMIN, "71428793860");
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        adminSession = login(ADMIN);
    }

    // A limpeza padrão preserva administradores; o criado aqui sai ao final
    @AfterEach
    void removeTestAdmin() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@contabilizape.com.br')");
        DatabaseCleanup.removeNonAdminUsers(jdbc);
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

    // ---------- contador da fila: queue.changed (SCRUM-189) ----------

    @Test
    void openingATicketSendsTheQueueSizeToTheHandlersOnly() throws Exception {
        queueTicket(joao()); // já havia um chamado esperando: a contagem é o total, e não "+1"
        MockHttpServletResponse handlerStream = openStream(login(HANDLER));
        MockHttpServletResponse adminStream = openStream(adminSession);
        MockHttpServletResponse collaboratorStream = openStream(login(JOAO));

        openTicket(login(MARIA), "Monitor piscando");

        for (MockHttpServletResponse stream : new MockHttpServletResponse[] {handlerStream, adminStream}) {
            assertThat(stream.getContentAsString())
                    .contains("event:queue.changed")
                    .contains("\"openCount\":2")
                    .contains("\"reason\":\"OPENED\"");
        }
        assertThat(collaboratorStream.getContentAsString()).doesNotContain("queue.changed");
    }

    @Test
    void assumingATicketLowersTheQueueSize() throws Exception {
        UUID waiting = queueTicket(maria);
        queueTicket(joao());
        MockHttpServletResponse adminStream = openStream(adminSession);

        mockMvc.perform(patch("/tickets/" + waiting + "/assign").cookie(login(HANDLER))).andExpect(status().isOk());

        assertThat(adminStream.getContentAsString())
                .contains("event:queue.changed")
                .contains("\"openCount\":1")
                .contains("\"reason\":\"ASSIGNED\"")
                .contains(waiting.toString());
    }

    @Test
    void refusedAssignmentDoesNotEmitAnything() throws Exception {
        UUID inProgress = ticket(handler);
        MockHttpServletResponse adminStream = openStream(adminSession);

        mockMvc.perform(patch("/tickets/" + inProgress + "/assign").cookie(login(HANDLER))).andExpect(status().isConflict());

        assertThat(adminStream.getContentAsString()).doesNotContain("queue.changed");
    }

    @Test
    void reassigningATicketFromTheQueueLowersTheQueueSize() throws Exception {
        UUID waiting = queueTicket(maria);
        MockHttpServletResponse handlerStream = openStream(login(HANDLER));

        mockMvc.perform(patch("/tickets/" + waiting + "/reassign").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"assigneeId\": \"" + handler + "\" }"))
                .andExpect(status().isOk());

        assertThat(handlerStream.getContentAsString())
                .contains("\"openCount\":0")
                .contains("\"reason\":\"ASSIGNED\"");
    }

    @Test
    void reassigningATicketInProgressLeavesTheQueueAlone() throws Exception {
        UUID inProgress = ticket(handler);
        UUID colleague = register(COLLEAGUE, "98765432100");
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", UUID.fromString(INNOVATION_ID), colleague);
        MockHttpServletResponse handlerStream = openStream(login(HANDLER));

        mockMvc.perform(patch("/tickets/" + inProgress + "/reassign").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"assigneeId\": \"" + colleague + "\" }"))
                .andExpect(status().isOk());

        assertThat(handlerStream.getContentAsString()).doesNotContain("queue.changed");
    }

    @Test
    void closingATicketThatWasStillInTheQueueLowersTheQueueSize() throws Exception {
        UUID waiting = queueTicket(maria);
        MockHttpServletResponse adminStream = openStream(adminSession);

        close(waiting);

        assertThat(adminStream.getContentAsString())
                .contains("\"openCount\":0")
                .contains("\"reason\":\"CLOSED\"");
    }

    @Test
    void closingATicketInProgressLeavesTheQueueAlone() throws Exception {
        UUID inProgress = ticket(handler);
        MockHttpServletResponse adminStream = openStream(adminSession);

        close(inProgress);

        assertThat(adminStream.getContentAsString()).doesNotContain("queue.changed");
    }

    @Test
    void deactivatingSomeoneReturnsTheirTicketsAndTheQueueGrowsAtOnce() throws Exception {
        ticket(handler);
        ticket(handler);
        queueTicket(joao());
        MockHttpServletResponse adminStream = openStream(adminSession);

        mockMvc.perform(patch("/users/" + handler + "/deactivate").cookie(adminSession)).andExpect(status().isOk());

        // Um evento só, com o total: 1 que já esperava + 2 devolvidos
        assertThat(adminStream.getContentAsString())
                .contains("\"openCount\":3")
                .contains("\"reason\":\"RETURNED\"");
    }

    // ---------- robustez do stream (SCRUM-190) ----------

    @Test
    void streamAsksProxiesNotToBufferIt() throws Exception {
        MockHttpServletResponse stream = openStream(login(MARIA));

        // O nginx (e proxies parecidos) seguram a resposta em buffer e o evento só chegaria depois
        assertThat(stream.getHeader("X-Accel-Buffering")).isEqualTo("no");
    }

    @Test
    void approvingTheRequestClosesTheStreamOfThePersonWhoAsked() throws Exception {
        UUID paulo = register(PAULO, "27946934092", INNOVATION_ID);
        String request = jdbc.queryForObject("SELECT id FROM department_requests WHERE user_id = ?", String.class, paulo);
        UUID ticket = ticketOf(paulo, handler);
        Cookie handlerSession = login(HANDLER);
        MockHttpServletResponse pauloStream = openStream(login(PAULO));

        comment(ticket, handlerSession, "Antes da aprovação");
        assertThat(pauloStream.getContentAsString(StandardCharsets.UTF_8)).contains("Antes da aprovação");

        mockMvc.perform(patch("/department-requests/" + request + "/approve").cookie(adminSession))
                .andExpect(status().isOk());
        comment(ticket, handlerSession, "Depois da aprovação");

        // A conexão antiga foi fechada: o navegador reconecta renovando a sessão, já com as permissões novas
        assertThat(pauloStream.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("Depois da aprovação");
    }

    // ---------- auxiliares ----------

    private MockHttpServletResponse openStream(Cookie session) throws Exception {
        return mockMvc.perform(get("/events/stream").cookie(session).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
    }

    private UUID register(String email, String cpf) throws Exception {
        return register(email, cpf, GENERAL_ID);
    }

    // Pedir a Inovação cria o pedido pendente; a conta começa no Geral
    private UUID register(String email, String cpf, String departmentId) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Maria da Silva", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(cpf, email, PASSWORD, departmentId)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private UUID joao() {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, JOAO);
    }

    private void openTicket(Cookie session, String title) throws Exception {
        String category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", String.class);
        mockMvc.perform(post("/tickets").cookie(session).contentType(MediaType.APPLICATION_JSON).content("""
                        { "title": "%s", "description": "Desde ontem.", "categoryId": "%s", "priorityId": 2 }
                        """.formatted(title, category)))
                .andExpect(status().isCreated());
    }

    private void close(UUID ticket) throws Exception {
        mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(login(HANDLER))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"resolution\": \"Resolvido.\" }"))
                .andExpect(status().isOk());
    }

    private void comment(UUID ticket, Cookie session, String content) throws Exception {
        mockMvc.perform(post("/tickets/" + ticket + "/comments").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"content\": \"" + content + "\" }"))
                .andExpect(status().isCreated());
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
        return ticketOf(maria, assignee);
    }

    // Chamado esperando na fila: OPEN, sem responsável
    private UUID queueTicket(UUID requester) {
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id)
                VALUES ('Na fila', 'Aguardando', 'OPEN', ?, 2, ?) RETURNING id
                """, UUID.class, category, requester);
    }

    private UUID ticketOf(UUID requester, UUID assignee) {
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id)
                VALUES ('Impressora', 'Sem papel', 'IN_PROGRESS', ?, 2, ?, ?) RETURNING id
                """, UUID.class, category, requester, assignee);
    }
}
