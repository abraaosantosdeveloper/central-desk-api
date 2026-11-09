package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
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
 * Exportação de chamados em planilha (GET /tickets/export, SCRUM-222): administrador exporta tudo, com todos os
 * campos do chamado; coordenador só o próprio setor (origem ou destino); os demais recebem 403; CPF e e-mail não saem.
 *
 * Cenário: Financeiro abriu "Impressora" para a Inovação (na fila); Fiscal abriu "VPN caiu" para a Inovação
 * (com Carlos) e "Nota fiscal" para o Financeiro (encerrado por Carlos, com resolução); a Inovação abriu "Servidor lento".
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

    private UUID invoice;

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

        ticket("Impressora", "Não imprime", maria, finance, INNOVATION, "OPEN", null, category);
        ticket("VPN caiu", "Sem acesso", joao, fiscal, INNOVATION, "IN_PROGRESS", carlos, category);
        invoice = ticket("Nota fiscal", "Emitir NF", joao, fiscal, finance, "CLOSED", carlos, category);
        jdbc.update("UPDATE tickets SET closed_by_id = ?, closed_at = now(), resolution = 'NF emitida' WHERE id = ?",
                carlos, invoice);
        ticket("Servidor lento", "Lentidão", carlos, INNOVATION, INNOVATION, "OPEN", null, category);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void adminDownloadsEveryTicketWithEveryField() throws Exception {
        byte[] body = mockMvc.perform(get("/tickets/export").cookie(login(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".xlsx")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = workbook(body)) {
            Sheet sheet = workbook.getSheet("Chamados");
            assertThat(sheet.getLastRowNum()).isEqualTo(4); // cabeçalho + 4 chamados, em ordem de número
            assertThat(texts(sheet.getRow(1))).contains("Impressora", "Não imprime", "Aberto", "Maria da Silva",
                    "Financeiro", "Inovação");
            assertThat(sheet.getRow(1).getCell(9).getCellType()).isEqualTo(CellType.BLANK); // sem responsável
            assertThat(sheet.getRow(1).getCell(14).getCellType()).isEqualTo(CellType.BLANK); // não encerrado

            Row closed = sheet.getRow(3);
            assertThat(texts(closed)).contains("Nota fiscal", "Encerrado", "João Souza", "Fiscal", "Financeiro",
                    "Carlos Lima", "NF emitida");
            assertThat(closed.getCell(4).getStringCellValue()).isNotBlank(); // categoria
            assertThat(closed.getCell(5).getStringCellValue()).isNotBlank(); // prioridade
            assertThat(closed.getCell(13).getStringCellValue()).isEqualTo("Carlos Lima"); // encerrado por
            assertThat(closed.getCell(14).getLocalDateTimeCellValue()).isNotNull(); // encerrado em
            assertThat(closed.getCell(17).getStringCellValue()).isEqualTo(invoice.toString()); // ID
        }
    }

    @Test
    void coordinatorDownloadsOnlyTicketsTheSectorOpenedOrReceived() throws Exception {
        byte[] body = mockMvc.perform(get("/tickets/export").cookie(login(PAULA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = workbook(body)) {
            Sheet sheet = workbook.getSheet("Chamados");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Impressora");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Nota fiscal");
        }
    }

    @Test
    void personalDataNeverLeaves() throws Exception {
        byte[] body = mockMvc.perform(get("/tickets/export").cookie(login(ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = workbook(body)) {
            Sheet sheet = workbook.getSheet("Chamados");
            for (Row row : sheet) {
                assertThat(String.join("|", texts(row)))
                        .doesNotContain(MARIA_CPF)
                        .doesNotContain("@contabilizape.com.br");
            }
        }
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

    private static XSSFWorkbook workbook(byte[] body) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(body));
    }

    private static List<String> texts(Row row) {
        List<String> texts = new ArrayList<>();
        for (Cell cell : row) {
            if (cell.getCellType() == CellType.STRING) {
                texts.add(cell.getStringCellValue());
            }
        }
        return texts;
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

    private UUID ticket(String title, String description, UUID requester, UUID origin, UUID target, String status,
                        UUID assignee, UUID category) {
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id,
                                     origin_department_id, target_department_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, now(), now())
                RETURNING id
                """, UUID.class, title, description, status, category, requester, assignee, origin, target);
    }
}
