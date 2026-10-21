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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestão de categorias pelo administrador (SCRUM-167): criar, editar e arquivar.
 */
@IntegrationTest
class CategoryIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String PASSWORD = "senhaForte123";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String MARIA = "maria.silva@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie adminSession;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        UUID admin = register(ADMIN, "71428793860");
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        adminSession = login(ADMIN);
    }

    @AfterEach
    void cleanUp() {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        jdbc.update("DELETE FROM categories WHERE label LIKE '[T]%'");
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email = ?)", ADMIN);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void adminCreatesEditsAndArchivesACategory() throws Exception {
        String id = create("[T] Telefonia", "#0891b2");

        mockMvc.perform(get("/categories").cookie(adminSession))
                .andExpect(jsonPath("$[*].label", hasItem("[T] Telefonia")));

        mockMvc.perform(patch("/categories/" + id).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[T] Telefonia fixa", "#7C3AED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("[T] Telefonia fixa"))
                .andExpect(jsonPath("$.color").value("#7C3AED"));

        mockMvc.perform(delete("/categories/" + id).cookie(adminSession)).andExpect(status().isNoContent());

        // Arquivada: some do formulário, mas o registro continua (histórico dos chamados)
        mockMvc.perform(get("/categories").cookie(adminSession))
                .andExpect(jsonPath("$[*].label", not(hasItem("[T] Telefonia fixa"))));
        assertThat(jdbc.queryForObject("SELECT archived_at IS NOT NULL FROM categories WHERE id = ?::uuid", Boolean.class, id))
                .isTrue();
    }

    @Test
    void archivedCategoryKeepsOldTicketsButRejectsNewOnes() throws Exception {
        String id = create("[T] Impressoras", "#DB2777");
        UUID maria = register(MARIA, "52998224725");
        Cookie mariaSession = login(MARIA);
        jdbc.update("""
                INSERT INTO tickets (title, description, category_id, priority_id, requester_id)
                VALUES ('Antigo', 'Chamado de antes do arquivamento', ?::uuid, 2, ?)
                """, id, maria);
        mockMvc.perform(delete("/categories/" + id).cookie(adminSession)).andExpect(status().isNoContent());

        mockMvc.perform(get("/tickets/mine").cookie(mariaSession))
                .andExpect(jsonPath("$.content[0].category.label").value("[T] Impressoras"));
        mockMvc.perform(post("/tickets").cookie(mariaSession).contentType(MediaType.APPLICATION_JSON).content("""
                        { "title": "Toner acabou", "description": "A impressora do RH está sem toner.",
                          "categoryId": "%s", "priorityId": 2 }
                        """.formatted(id)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usageCountsTicketsPerCategory() throws Exception {
        String id = create("[T] Uso", "#0D9488");
        UUID maria = register(MARIA, "52998224725");
        jdbc.update("""
                INSERT INTO tickets (title, description, category_id, priority_id, requester_id)
                VALUES ('Um', 'Descrição', ?::uuid, 2, ?), ('Dois', 'Descrição', ?::uuid, 2, ?)
                """, id, maria, id, maria);

        mockMvc.perform(get("/categories/usage").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categoryId == '%s')].tickets".formatted(id), hasItem(2)));
    }

    @Test
    void invalidDataIsRejected() throws Exception {
        create("[T] Única", "#0D9488");

        mockMvc.perform(post("/categories").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[T] única", "#0D9488")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/categories").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[T] Cor ruim", "azul")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.color").exists());
        mockMvc.perform(post("/categories").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(body(" ", "#0D9488")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.label").exists());
        mockMvc.perform(patch("/categories/" + UUID.randomUUID()).cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(body("[T] X", "#0D9488")))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsManageCategories() throws Exception {
        register(MARIA, "52998224725");
        Cookie mariaSession = login(MARIA);

        mockMvc.perform(post("/categories").cookie(mariaSession).contentType(MediaType.APPLICATION_JSON)
                        .content(body("[T] Intrusa", "#0D9488")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/categories/usage").cookie(mariaSession)).andExpect(status().isForbidden());
        // A lista do formulário continua aberta a qualquer usuário logado
        mockMvc.perform(get("/categories").cookie(mariaSession)).andExpect(status().isOk());
    }

    // ---------- auxiliares ----------

    private String create(String label, String color) throws Exception {
        String json = mockMvc.perform(post("/categories").cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(body(label, color)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private static String body(String label, String color) {
        return "{ \"label\": \"%s\", \"color\": \"%s\" }".formatted(label, color);
    }

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
}
