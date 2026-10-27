package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
import br.com.contabilizape.centraldesk.RecordingEmailSender;
import br.com.contabilizape.centraldesk.email.EmailMessage;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-184: e-mails dos chamados, pela matriz revisada (PLANO-SEGURANCA §13.1). Quem fez a ação não
 * recebe, com a exceção da reatribuição; um e-mail por pessoa; só contas ativas.
 */
@IntegrationTest
class TicketEmailIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String CARLOS = "carlos.inovacao@contabilizape.com.br";
    private static final String BEATRIZ = "beatriz.inovacao@contabilizape.com.br";
    private static final String ANA = "ana.admin@contabilizape.com.br";
    private static final String BRUNO = "bruno.admin@contabilizape.com.br";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecordingEmailSender emails;

    private UUID carlos;
    private UUID beatriz;
    private UUID ana;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        register(MARIA, "52998224725", "Maria da Silva");
        carlos = register(CARLOS, "39053344705", "Carlos Lima");
        beatriz = register(BEATRIZ, "11144477735", "Beatriz Souza");
        ana = register(ANA, "71428793860", "Ana Admin");
        UUID bruno = register(BRUNO, "86288366757", "Bruno Admin");
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id IN (?, ?, ?, ?)", INNOVATION_ID, carlos, beatriz, ana, bruno);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?), (?)", ana, bruno);
        emails.clear();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email IN (?, ?))", ANA, BRUNO);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // ---------- abrir ----------

    @Test
    void openingWarnsTheRequesterAndTheDepartmentAdmins() throws Exception {
        open(MARIA);

        assertThat(subjects()).containsOnly(
                Map.entry(MARIA, "Chamado Aberto"), Map.entry(ANA, "Chamado Aberto"), Map.entry(BRUNO, "Chamado Aberto"));
        assertThat(only(MARIA).html()).contains("Recebemos o seu chamado");
        assertThat(only(ANA).html()).contains("Maria da Silva").contains("abriu um chamado");
    }

    @Test
    void adminWhoOpensGetsOnlyTheRequesterVersion() throws Exception {
        open(ANA);

        assertThat(subjects()).containsOnlyKeys(ANA, BRUNO);
        assertThat(only(ANA).html()).contains("Recebemos o seu chamado");
    }

    // ---------- assumir ----------

    @Test
    void assumingWarnsOnlyTheRequester() throws Exception {
        UUID ticket = open(MARIA);
        emails.clear();

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(login(CARLOS))).andExpect(status().isOk());

        assertThat(subjects()).containsOnly(Map.entry(MARIA, "Chamado em andamento"));
        assertThat(only(MARIA).html()).contains("Carlos Lima");
    }

    @Test
    void adminAssumingHisOwnTicketWarnsNobody() throws Exception {
        UUID ticket = open(ANA);
        emails.clear();

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(login(ANA))).andExpect(status().isOk());

        assertThat(emails.messages()).isEmpty();
    }

    // ---------- reatribuir ----------

    @Test
    void adminReassigningWarnsRequesterNewAndPreviousAssigneeButNotHimself() throws Exception {
        UUID ticket = assumedBy(open(MARIA), CARLOS);

        reassign(ticket, beatriz, ANA).andExpect(status().isOk());

        assertThat(subjects()).containsOnly(Map.entry(MARIA, "Chamado reatribuído"), Map.entry(BEATRIZ, "Chamado reatribuído"),
                Map.entry(CARLOS, "Chamado reatribuído"));
        assertThat(only(MARIA).html()).contains("agora quem atende é").contains("Beatriz Souza");
        assertThat(only(BEATRIZ).html()).contains("passou este chamado para você");
        assertThat(only(CARLOS).html()).contains("Você não é mais o responsável");
    }

    @Test
    void assigneeWhoHandsTheTicketOverStillGetsThePreviousAssigneeEmail() throws Exception {
        UUID ticket = assumedBy(open(MARIA), CARLOS);

        reassign(ticket, beatriz, CARLOS).andExpect(status().isOk());

        assertThat(subjects()).containsOnlyKeys(MARIA, BEATRIZ, CARLOS);
        assertThat(only(CARLOS).html()).contains("Você não é mais o responsável");
    }

    @Test
    void adminTakingTheTicketForHimselfGetsTheNewAssigneeEmail() throws Exception {
        UUID ticket = assumedBy(open(MARIA), CARLOS);

        reassign(ticket, ana, ANA).andExpect(status().isOk());

        assertThat(subjects()).containsOnlyKeys(MARIA, ANA, CARLOS);
        assertThat(only(ANA).html()).contains("passou este chamado para você");
    }

    @Test
    void reassigningAnAdminsOwnTicketSendsHimOneEmailOnly() throws Exception {
        UUID ticket = assumedBy(open(ANA), ANA);

        reassign(ticket, carlos, BRUNO).andExpect(status().isOk());

        // Ana é solicitante e responsável anterior: recebe só o e-mail do papel mais específico
        assertThat(emails.to(ANA)).hasSize(1);
        assertThat(only(ANA).html()).contains("Você não é mais o responsável");
        assertThat(subjects()).containsOnlyKeys(ANA, CARLOS);
    }

    // ---------- comentar ----------

    @Test
    void commentsGoOnlyToTheOtherSideAtThatMoment() throws Exception {
        UUID ticket = assumedBy(open(MARIA), CARLOS);

        comment(ticket, MARIA, "A impressora voltou a falhar.\nPode olhar de novo?");
        assertThat(subjects()).containsOnly(Map.entry(CARLOS, "Nova mensagem no chamado #" + number(ticket)));
        assertThat(only(CARLOS).html()).contains("A impressora voltou a falhar.").contains("Pode olhar de novo?");

        emails.clear();
        comment(ticket, CARLOS, "Vou passar aí.");
        assertThat(subjects()).containsOnlyKeys(MARIA);

        reassign(ticket, beatriz, CARLOS).andExpect(status().isOk());
        emails.clear();
        comment(ticket, MARIA, "Alguma novidade?");
        assertThat(subjects()).containsOnlyKeys(BEATRIZ);
    }

    @Test
    void commentInTheQueueOrOnOwnInternalTicketSendsNothing() throws Exception {
        UUID inQueue = open(MARIA);
        UUID internal = assumedBy(open(ANA), ANA);
        emails.clear();

        comment(inQueue, MARIA, "Esqueci de dizer: é a do 2º andar.");
        comment(internal, ANA, "Anotação da demanda interna.");

        assertThat(emails.messages()).isEmpty();
    }

    @Test
    void inactiveRecipientIsSkipped() throws Exception {
        UUID ticket = assumedBy(open(MARIA), CARLOS);
        Cookie carlosSession = login(CARLOS);
        jdbc.update("UPDATE users SET status = 'DEACTIVATED', deactivated_at = now() WHERE email = ?", MARIA);

        mockMvc.perform(post("/tickets/" + ticket + "/comments").cookie(carlosSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"content\": \"Resolvido?\" }"))
                .andExpect(status().isCreated());

        assertThat(emails.messages()).isEmpty();
    }

    // ---------- encerrar ----------

    @Test
    void assigneeClosingWarnsOnlyTheRequester() throws Exception {
        UUID ticket = assumedBy(open(MARIA), CARLOS);

        close(ticket, CARLOS).andExpect(status().isOk());

        assertThat(subjects()).containsOnly(Map.entry(MARIA, "Chamado Encerrado"));
        assertThat(only(MARIA).html()).contains("Carlos Lima").contains("Troquei a fonte.");
    }

    @Test
    void adminClosingSomeoneElsesTicketWarnsRequesterAndAssignee() throws Exception {
        UUID ticket = assumedBy(open(MARIA), CARLOS);

        close(ticket, ANA).andExpect(status().isOk());

        assertThat(subjects()).containsOnly(Map.entry(MARIA, "Chamado Encerrado"), Map.entry(CARLOS, "Chamado Encerrado"));
    }

    @Test
    void refusedOperationSendsNothing() throws Exception {
        UUID ticket = assumedBy(open(MARIA), CARLOS);

        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(login(BEATRIZ))).andExpect(status().isConflict());

        assertThat(emails.messages()).isEmpty();
    }

    // ---------- auxiliares ----------

    private Map<String, String> subjects() {
        return emails.messages().stream().collect(Collectors.toMap(EmailMessage::to, EmailMessage::subject,
                (first, second) -> { throw new AssertionError("Mais de um e-mail para a mesma pessoa: " + emails.messages()); }));
    }

    private EmailMessage only(String email) {
        assertThat(emails.to(email)).hasSize(1);
        return emails.to(email).getFirst();
    }

    private UUID open(String email) throws Exception {
        UUID category = jdbc.queryForObject("SELECT id FROM categories WHERE archived_at IS NULL LIMIT 1", UUID.class);
        String body = mockMvc.perform(post("/tickets").cookie(login(email)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "Impressora", "description": "A impressora do 2º andar mostra erro de papel.",
                                  "categoryId": "%s", "priorityId": 2 }""".formatted(category)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID assumedBy(UUID ticket, String email) throws Exception {
        mockMvc.perform(patch("/tickets/" + ticket + "/assign").cookie(login(email))).andExpect(status().isOk());
        emails.clear();
        return ticket;
    }

    private ResultActions reassign(UUID ticket, UUID assignee, String actor) throws Exception {
        return mockMvc.perform(patch("/tickets/" + ticket + "/reassign").cookie(login(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"assigneeId\": \"%s\" }".formatted(assignee)));
    }

    private void comment(UUID ticket, String author, String content) throws Exception {
        mockMvc.perform(post("/tickets/" + ticket + "/comments").cookie(login(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": \"%s\" }".formatted(content.replace("\n", "\\n"))))
                .andExpect(status().isCreated());
    }

    private ResultActions close(UUID ticket, String actor) throws Exception {
        return mockMvc.perform(patch("/tickets/" + ticket + "/close").cookie(login(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{ \"resolution\": \"Troquei a fonte.\" }"));
    }

    private long number(UUID ticket) {
        return jdbc.queryForObject("SELECT ticket_number FROM tickets WHERE id = ?", Long.class, ticket);
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
