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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Abertura de chamado (POST /tickets) e opções do formulário (GET /categories, GET /priorities).
 */
@IntegrationTest
class TicketControllerIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String EMAIL = "maria.silva@contabilizape.com.br";
    private static final String HANDLER = "carlos.inovacao@contabilizape.com.br";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String PASSWORD = "senhaForte123";
    private static final Pattern TOKEN_IN_COOKIE = Pattern.compile("access_token=([^;]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie session;
    private String hardwareId;

    @BeforeEach
    void registerAndLogin() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "Maria da Silva", "cpf": "52998224725", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(EMAIL, PASSWORD, GENERAL_ID)))
                .andExpect(status().isCreated());
        String setCookie = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        Matcher matcher = TOKEN_IN_COOKIE.matcher(setCookie);
        assertThat(matcher.find()).isTrue();
        session = new Cookie("access_token", matcher.group(1));
        hardwareId = jdbc.queryForObject("SELECT id FROM categories WHERE label = 'Serviços'", String.class);
    }

    // A limpeza padrão preserva administradores; o criado aqui sai ao final
    @AfterEach
    void removeTestAdmin() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // ---------- abertura ----------

    @Test
    void opensTicketAsTheLoggedUser() throws Exception {
        String location = mockMvc.perform(openTicket(body("Impressora parada", hardwareId, "2")).cookie(session))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/tickets/")))
                .andExpect(jsonPath("$.title").value("Impressora parada"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.category.label").value("Serviços"))
                .andExpect(jsonPath("$.priority.label").value("Média"))
                .andExpect(jsonPath("$.requesterName").value("Maria da Silva"))
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        UUID ticketId = UUID.fromString(location.substring("/tickets/".length()));
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT t.status, u.email FROM tickets t JOIN users u ON u.id = t.requester_id WHERE t.id = ?
                """, ticketId);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("email")).isEqualTo(EMAIL);
    }

    @Test
    void requesterAndStatusComeFromTheServerNotFromTheBody() throws Exception {
        String body = """
                { "title": "Sem acesso ao sistema", "description": "Detalhes", "categoryId": "%s", "priorityId": 1,
                  "requesterId": "%s", "status": "CLOSED" }
                """.formatted(hardwareId, UUID.randomUUID());

        mockMvc.perform(openTicket(body).cookie(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.requesterName").value("Maria da Silva"));
    }

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(openTicket(body("Impressora parada", hardwareId, "2")))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class)).isZero();
    }

    @Test
    void invalidFieldsAreListed() throws Exception {
        mockMvc.perform(openTicket("""
                        { "title": "", "description": "Detalhes", "priorityId": 2 }
                        """).cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists());
    }

    @Test
    void unknownCategoryOrPriorityIsBadRequest() throws Exception {
        mockMvc.perform(openTicket(body("Título", UUID.randomUUID().toString(), "2")).cookie(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(openTicket(body("Título", hardwareId, "9")).cookie(session))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets", Integer.class)).isZero();
    }

    // ---------- detalhes ----------

    @Test
    void requesterSeesTicketDetails() throws Exception {
        UUID ticketId = openTicketAndGetId();

        mockMvc.perform(get("/tickets/" + ticketId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.title").value("Impressora parada"))
                .andExpect(jsonPath("$.description").value("A impressora do 2º andar mostra erro de papel."))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.requesterName").value("Maria da Silva"))
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.closedAt").doesNotExist());
    }

    @Test
    void ticketHandlerSeesAnyTicket() throws Exception {
        UUID ticketId = openTicketAndGetId();

        mockMvc.perform(get("/tickets/" + ticketId).cookie(registerHandlerAndLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requesterName").value("Maria da Silva"));
    }

    @Test
    void adminSeesAnyTicket() throws Exception {
        UUID ticketId = openTicketAndGetId();

        mockMvc.perform(get("/tickets/" + ticketId).cookie(registerAdminAndLogin()))
                .andExpect(status().isOk());
    }

    @Test
    void anotherCollaboratorGetsNotFound() throws Exception {
        UUID ticketId = openTicketAndGetId();
        Cookie joao = registerAndLogin("joao.souza@contabilizape.com.br", "11144477735");

        mockMvc.perform(get("/tickets/" + ticketId).cookie(joao))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownTicketDetailsAreNotFound() throws Exception {
        mockMvc.perform(get("/tickets/" + UUID.randomUUID()).cookie(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void ticketDetailsWithoutSessionIsUnauthorized() throws Exception {
        UUID ticketId = openTicketAndGetId();

        mockMvc.perform(get("/tickets/" + ticketId))
                .andExpect(status().isUnauthorized());
    }

    // ---------- edição ----------

    @Test
    void requesterEditsOpenTicket() throws Exception {
        UUID ticketId = openTicketAndGetId();

        mockMvc.perform(editTicket(ticketId, body("Título corrigido", hardwareId, "3")).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Título corrigido"))
                .andExpect(jsonPath("$.priority.label").value("Alta"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        Map<String, Object> row = jdbc.queryForMap("SELECT title, priority_id, updated_at FROM tickets WHERE id = ?", ticketId);
        assertThat(row.get("title")).isEqualTo("Título corrigido");
        assertThat(((Number) row.get("priority_id")).intValue()).isEqualTo(3);
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void anotherUserCannotEdit() throws Exception {
        UUID ticketId = openTicketAndGetId();
        Cookie joao = registerAndLogin("joao.souza@contabilizape.com.br", "11144477735");

        mockMvc.perform(editTicket(ticketId, body("Invasão", hardwareId, "4")).cookie(joao))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT title FROM tickets WHERE id = ?", String.class, ticketId))
                .isEqualTo("Impressora parada");
    }

    @Test
    void assumedTicketCannotBeEdited() throws Exception {
        UUID ticketId = openTicketAndGetId();
        jdbc.update("UPDATE tickets SET status = 'IN_PROGRESS' WHERE id = ?", ticketId);

        mockMvc.perform(editTicket(ticketId, body("Tarde demais", hardwareId, "2")).cookie(session))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownTicketIsNotFound() throws Exception {
        mockMvc.perform(editTicket(UUID.randomUUID(), body("Título", hardwareId, "2")).cookie(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void editWithoutSessionIsUnauthorized() throws Exception {
        UUID ticketId = openTicketAndGetId();

        mockMvc.perform(editTicket(ticketId, body("Título", hardwareId, "2")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editWithInvalidFieldsIsBadRequest() throws Exception {
        UUID ticketId = openTicketAndGetId();

        mockMvc.perform(editTicket(ticketId, """
                        { "title": "", "description": "", "categoryId": "%s", "priorityId": 2 }
                        """.formatted(hardwareId)).cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.description").exists());
    }

    // ---------- opções do formulário ----------

    @Test
    void listsCategoriesByName() throws Exception {
        mockMvc.perform(get("/categories").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].label").value(contains(
                        "Acesso e contas", "Outros", "Serviços", "Software")))
                .andExpect(jsonPath("$[0].color").value("#F59E0B"));
    }

    @Test
    void listsPrioritiesByUrgency() throws Exception {
        mockMvc.perform(get("/priorities").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].label").value(contains("Baixa", "Média", "Alta", "Crítica")))
                .andExpect(jsonPath("$[*].id").value(contains(1, 2, 3, 4)));
    }

    @Test
    void catalogRequiresSession() throws Exception {
        mockMvc.perform(get("/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/priorities")).andExpect(status().isUnauthorized());
    }

    // ---------- auxiliares ----------

    private UUID openTicketAndGetId() throws Exception {
        String location = mockMvc.perform(openTicket(body("Impressora parada", hardwareId, "2")).cookie(session))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        return UUID.fromString(location.substring("/tickets/".length()));
    }

    private Cookie registerAndLogin(String email, String cpf) throws Exception {
        register(email, cpf);
        return login(email);
    }

    // Quem pede a Inovação no cadastro espera aprovação; aqui o usuário é movido direto, como se aprovado
    private Cookie registerHandlerAndLogin() throws Exception {
        UUID id = register(HANDLER, "11144477735");
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", UUID.fromString(INNOVATION_ID), id);
        return login(HANDLER);
    }

    private Cookie registerAdminAndLogin() throws Exception {
        UUID id = register(ADMIN, "39053344705");
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", id);
        return login(ADMIN);
    }

    private UUID register(String email, String cpf) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "João Souza", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(cpf, email, PASSWORD, GENERAL_ID)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    private Cookie login(String email) throws Exception {
        String setCookie = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        Matcher matcher = TOKEN_IN_COOKIE.matcher(setCookie);
        assertThat(matcher.find()).isTrue();
        return new Cookie("access_token", matcher.group(1));
    }

    private static MockHttpServletRequestBuilder editTicket(UUID id, String body) {
        return put("/tickets/" + id).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder openTicket(String body) {
        return post("/tickets").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String body(String title, String categoryId, String priorityId) {
        return """
                { "title": "%s", "description": "A impressora do 2º andar mostra erro de papel.",
                  "categoryId": "%s", "priorityId": %s }
                """.formatted(title, categoryId, priorityId);
    }
}
