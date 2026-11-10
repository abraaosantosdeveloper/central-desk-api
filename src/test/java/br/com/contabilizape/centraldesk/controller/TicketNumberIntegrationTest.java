package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Número do chamado (SCRUM-181): sequencial, gerado pelo banco (V17) e exibido como #n.
 * O id continua sendo o UUID; o número só existe para as pessoas (e-mails e interface).
 */
@IntegrationTest
class TicketNumberIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String EMAIL = "maria.silva@contabilizape.com.br";
    private static final String PASSWORD = "senhaForte123";

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
        String header = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith("access_token=")).findFirst().orElseThrow();
        session = new Cookie("access_token", header.substring(header.indexOf('=') + 1, header.indexOf(';')));
        hardwareId = jdbc.queryForObject("SELECT id FROM categories WHERE label = 'Serviços'", String.class);
    }

    @AfterEach
    void cleanUp() {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void openingTicketsGivesConsecutiveNumbers() throws Exception {
        long first = openTicketAndReadNumber("Impressora parada", "");
        long second = openTicketAndReadNumber("Mouse quebrado", "");

        assertThat(first).isPositive();
        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    void numberAppearsInTheDetailsAndInTheListing() throws Exception {
        String body = mockMvc.perform(post("/tickets").cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("Impressora parada", "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        int number = JsonPath.read(body, "$.number");

        mockMvc.perform(get("/tickets/" + id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(number));
        mockMvc.perform(get("/tickets/mine").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].number").value(number));
    }

    @Test
    void numberComesFromTheDatabaseNotFromTheBody() throws Exception {
        long number = openTicketAndReadNumber("Sem acesso", ", \"number\": 999999");

        assertThat(number).isNotEqualTo(999999L);
    }

    @Test
    void databaseGeneratesTheNumberForTicketsInsertedWithoutIt() {
        UUID requester = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, EMAIL);

        jdbc.update("""
                INSERT INTO tickets (title, description, category_id, priority_id, requester_id)
                VALUES ('Inserido por SQL', 'Sem informar o número', ?::uuid, 2, ?)
                """, hardwareId, requester);

        Long number = jdbc.queryForObject(
                "SELECT ticket_number FROM tickets WHERE title = 'Inserido por SQL'", Long.class);
        assertThat(number).isNotNull().isPositive();
    }

    private long openTicketAndReadNumber(String title, String extraJsonFields) throws Exception {
        String body = mockMvc.perform(post("/tickets").cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(title, extraJsonFields)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.number")).longValue();
    }

    private String ticketBody(String title, String extraJsonFields) {
        return """
                { "title": "%s", "description": "Detalhes do problema", "categoryId": "%s", "priorityId": 2%s }
                """.formatted(title, hardwareId, extraJsonFields);
    }
}
