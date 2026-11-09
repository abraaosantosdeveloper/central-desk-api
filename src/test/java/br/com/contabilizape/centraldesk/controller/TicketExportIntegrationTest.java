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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exportação de chamados em CSV (GET /tickets/export, SCRUM-XXX): administrador exporta tudo, coordenador só o
 * próprio setor (origem ou destino), os demais recebem 403 e nenhum dado sensível sai no arquivo.
 *
 * Cenário: Financeiro abriu "Impressora" para a Inovação (na fila); Fiscal abriu "VPN caiu" para a Inovação
 * (com Carlos) e "Nota fiscal" para o Financeiro (encerrado por Carlos); a Inovação abriu "Servidor lento".
 * Paula coordena o Financeiro.
 */
@IntegrationTest
class TicketExportIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final UUID INNOVATION = UUID.fromString("e9790f1f-41df-4369-bfe7-1be4be535561");
    private static final String PASSWORD = "senhaForte123";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String PAULA = "paula.rocha@contabilizape.com.br";
    private static final String JOAO = "joao.souza@contabilizape.com.br";
    private static final String CARLOS = "carlos.inovacao@contabilizape.com.br";
    private static final String MARIA_CPF = "52998224725";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        UUID finance = departmentId("Financeiro");
        UUID fiscal = departmentId("Fiscal");
        UUID category = jdbc.queryForObject("SELECT id FROM categories ORDER BY label LIMIT 1", UUID.class);

        UUID admin = account(ADMIN, "71428793860", "Ana Admin", INNOVATION);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        UUID maria = account(MARIA, MARIA_CPF, "Maria da Silva", finance);
        UUID paula = account(PAULA, "11144477735", "Paula Rocha", finance);
        jdbc.update("INSERT INTO coordinators (user_id, granted_by_id) VALUES (?, ?)", paula, admin);
        UUID joao = account(JOAO, "86288366757", "João Souza", fiscal);
        UUID carlos = account(CARLOS, "39053344705", "Carlos Lima", INNOVATION);

        ticket("Impressora", "Não imprime; papel preso", maria, finance, INNOVATION, "OPEN", null, category);
        ticket("VPN caiu", "Sem acesso", joao, fiscal, INNOVATION, "IN_PROGRESS", carlos, category);
        ticket("Nota fiscal", "Emitir NF", joao, fiscal, finance, "CLOSED", carlos, category);
        ticket("Servidor lento", "Lentidão", carlos, INNOVATION, INNOVATION, "OPEN", null, category);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void adminDownloadsEveryTicketAsCsvFile() throws Exception {
        byte[] body = mockMvc.perform(get("/tickets/export").cookie(login(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("chamados-")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        List<String> lines = lines(body);
        assertThat(lines).hasSize(5); // cabeçalho + 4 chamados
        assertThat(lines.get(0)).startsWith("﻿Número;Título;Descrição;Solicitante;");
        assertThat(String.join("\n", lines))
                .contains(";Impressora;\"Não imprime; papel preso\";Maria da Silva;Financeiro;Inovação;Em espera;Aberto;")
                .contains(";VPN caiu;Sem acesso;João Souza;Fiscal;Inovação;Carlos Lima;Em andamento;")
                .contains(";Nota fiscal;Emitir NF;João Souza;Fiscal;Financeiro;Carlos Lima;Encerrado;")
                .contains(";Servidor lento;");
    }

    @Test
    void coordinatorDownloadsOnlyTicketsTheSectorOpenedOrReceived() throws Exception {
        byte[] body = mockMvc.perform(get("/tickets/export").cookie(login(PAULA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        String csv = String.join("\n", lines(body));
        assertThat(lines(body)).hasSize(3);
        assertThat(csv).contains(";Impressora;").contains(";Nota fiscal;")
                .doesNotContain(";VPN caiu;").doesNotContain(";Servidor lento;");
    }

    @Test
    void sensitiveDataNeverLeaves() throws Exception {
        byte[] body = mockMvc.perform(get("/tickets/export").cookie(login(ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(body, StandardCharsets.UTF_8))
                .doesNotContain(MARIA_CPF)
                .doesNotContain("@contabilizape.com.br")
                .doesNotContain(INNOVATION.toString());
    }

    @Test
    void collaboratorAndInnovationStaffAreForbidden() throws Exception {
        mockMvc.perform(get("/tickets/export").cookie(login(MARIA))).andExpect(status().isForbidden());
        mockMvc.perform(get("/tickets/export").cookie(login(CARLOS))).andExpect(status().isForbidden());
    }

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get("/tickets/export")).andExpect(status().isUnauthorized());
    }

    // Quebras de linha só separam registros fora de aspas; nos dados deste teste não há quebra dentro de campo
    private static List<String> lines(byte[] body) {
        return Arrays.asList(new String(body, StandardCharsets.UTF_8).split("\r\n"));
    }

    private UUID departmentId(String name) {
        return jdbc.queryForObject("SELECT id FROM departments WHERE name = ?", UUID.class, name);
    }

    private UUID account(String email, String cpf, String name, UUID department) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "%s", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(name, cpf, email, PASSWORD, GENERAL_ID)))
                .andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", department, id);
        return id;
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

    private void ticket(String title, String description, UUID requester, UUID origin, UUID target, String status,
                        UUID assignee, UUID category) {
        jdbc.update("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id,
                                     origin_department_id, target_department_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, now(), now())
                """, title, description, status, category, requester, assignee, origin, target);
    }
}
