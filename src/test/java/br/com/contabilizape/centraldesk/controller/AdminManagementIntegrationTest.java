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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administração de contas: conceder e remover admin (SCRUM-169), trocar o departamento (SCRUM-170),
 * filtrar a listagem por perfil e departamento (SCRUM-168) e corrigir os dados de uma conta (SCRUM-176).
 */
@IntegrationTest
class AdminManagementIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String JOAO = "joao.souza@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID admin;
    private Cookie adminSession;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        admin = register(ADMIN, "71428793860", "Ana Admin", GENERAL_ID);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        adminSession = login(ADMIN);
    }

    @AfterEach
    void removeTestAdmins() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@contabilizape.com.br')");
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    // ---------- SCRUM-170: departamentos ----------

    @Test
    void allCompanyDepartmentsAreAvailableAtSignUp() throws Exception {
        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Inovação", "Geral", "Financeiro", "Fiscal",
                        "Contábil", "Recursos Humanos")));
    }

    @Test
    void adminMovesAnAccountToAnotherDepartment() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        UUID finance = departmentId("Financeiro");

        mockMvc.perform(patch("/users/" + maria + "/department").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(departmentBody(finance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Financeiro"))
                .andExpect(jsonPath("$.ticketHandler").value(false));

        // Para a Inovação, a decisão do admin já vale como aprovação
        mockMvc.perform(patch("/users/" + maria + "/department").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(departmentBody(UUID.fromString(INNOVATION_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketHandler").value(true));
    }

    @Test
    void leavingInnovationReturnsTicketsInProgressToTheQueue() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        UUID joao = register(JOAO, "11144477735", "João Souza", GENERAL_ID);
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, joao);
        UUID ticket = ticket(maria, joao);

        mockMvc.perform(patch("/users/" + joao + "/department").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(departmentBody(UUID.fromString(GENERAL_ID))))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket)).isEqualTo("OPEN");
    }

    @Test
    void invalidDepartmentChangesAreRejected() throws Exception {
        UUID pending = register(MARIA, "52998224725", "Maria da Silva", INNOVATION_ID);
        UUID joao = register(JOAO, "11144477735", "João Souza", GENERAL_ID);
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE id = ?", joao);
        String finance = departmentBody(departmentId("Financeiro"));

        // Pedido de entrada na Inovação pendente: o admin decide o pedido primeiro
        mockMvc.perform(patch("/users/" + pending + "/department").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(finance))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/" + joao + "/department").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(finance))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/" + pending + "/department").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(departmentBody(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/users/" + pending + "/department").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- SCRUM-169: conceder e remover admin ----------

    @Test
    void adminGrantsAndRevokesAdminAccess() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, maria);

        mockMvc.perform(put("/users/" + maria + "/admin").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
        assertThat(jdbc.queryForObject("SELECT granted_by_id FROM admins WHERE user_id = ?", UUID.class, maria))
                .isEqualTo(admin);
        mockMvc.perform(put("/users/" + maria + "/admin").cookie(adminSession)).andExpect(status().isConflict());

        // A nova admin já usa o painel (o perfil vem do banco no próximo login)
        Cookie mariaSession = login(MARIA);
        mockMvc.perform(get("/users").cookie(mariaSession)).andExpect(status().isOk());

        mockMvc.perform(delete("/users/" + maria + "/admin").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(false));
        mockMvc.perform(delete("/users/" + maria + "/admin").cookie(adminSession)).andExpect(status().isConflict());
    }

    @Test
    void adminAccessRulesProtectTheSystem() throws Exception {
        UUID joao = register(JOAO, "11144477735", "João Souza", GENERAL_ID);
        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE id = ?", joao);

        // Só contas ativas viram admin
        mockMvc.perform(put("/users/" + joao + "/admin").cookie(adminSession)).andExpect(status().isConflict());
        // ...e só as da Inovação (SCRUM-176)
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        mockMvc.perform(put("/users/" + maria + "/admin").cookie(adminSession)).andExpect(status().isConflict());
        // Ninguém remove o próprio acesso (e assim o último admin nunca sai)
        mockMvc.perform(delete("/users/" + admin + "/admin").cookie(adminSession)).andExpect(status().isConflict());
        mockMvc.perform(put("/users/" + UUID.randomUUID() + "/admin").cookie(adminSession)).andExpect(status().isNotFound());
    }

    @Test
    void collaboratorCannotGrantAdmin() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        Cookie mariaSession = login(MARIA);

        mockMvc.perform(put("/users/" + maria + "/admin").cookie(mariaSession)).andExpect(status().isForbidden());
    }

    // ---------- SCRUM-176: corrigir os dados de uma conta ----------

    @Test
    void adminCorrectsTheDataOfAnAccount() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        Cookie mariaSession = login(MARIA);
        UUID finance = departmentId("Financeiro");

        mockMvc.perform(get("/users/" + maria).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value("52998224725"));

        mockMvc.perform(put("/users/" + maria).cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Maria Souza", "11144477735", "Maria.Souza@contabilizape.com.br", finance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Maria Souza"))
                .andExpect(jsonPath("$.cpf").value("11144477735"))
                .andExpect(jsonPath("$.email").value("maria.souza@contabilizape.com.br"))
                .andExpect(jsonPath("$.departmentName").value("Financeiro"));

        // O e-mail é o login: as sessões abertas caem e só o endereço novo entra
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, maria)).isZero();
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(MARIA, PASSWORD)))
                .andExpect(status().isUnauthorized());
        login("maria.souza@contabilizape.com.br");

        // A listagem continua sem o CPF
        mockMvc.perform(get("/users").param("search", "Maria Souza").cookie(adminSession))
                .andExpect(jsonPath("$.content[0].cpf").doesNotExist());

        // O access token que ela já tinha deixa de valer na hora. O corte foi gravado pela edição; aqui ele
        // é adiantado para o teste não depender de o login e a edição caírem em segundos diferentes.
        assertThat(jdbc.queryForObject("SELECT sessions_valid_from IS NOT NULL FROM users WHERE id = ?",
                Boolean.class, maria)).isTrue();
        jdbc.update("UPDATE users SET sessions_valid_from = now() + interval '1 minute' WHERE id = ?", maria);
        mockMvc.perform(get("/users/me").cookie(mariaSession)).andExpect(status().isUnauthorized());
        // A sessão de quem editou não é afetada
        mockMvc.perform(get("/users/me").cookie(adminSession)).andExpect(status().isOk());
    }

    @Test
    void invalidCorrectionsAreRejected() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, maria);
        register(JOAO, "11144477735", "João Souza", GENERAL_ID);
        UUID innovation = UUID.fromString(INNOVATION_ID);

        // CPF e e-mail de outra conta
        mockMvc.perform(put("/users/" + maria).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Maria da Silva", "11144477735", MARIA, innovation)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/users/" + maria).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Maria da Silva", "52998224725", JOAO, innovation)))
                .andExpect(status().isConflict());
        // CPF inválido e e-mail malformado (qualquer domínio é aceito na versão genérica)
        mockMvc.perform(put("/users/" + maria).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Maria da Silva", "12345678900", MARIA, innovation)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cpf").exists());
        mockMvc.perform(put("/users/" + maria).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Maria da Silva", "52998224725", "maria-sem-arroba", innovation)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());

        // Administradores ficam na Inovação
        mockMvc.perform(put("/users/" + maria + "/admin").cookie(adminSession)).andExpect(status().isOk());
        mockMvc.perform(put("/users/" + maria).cookie(adminSession).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Maria da Silva", "52998224725", MARIA, UUID.fromString(GENERAL_ID))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/" + maria + "/department").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content(departmentBody(UUID.fromString(GENERAL_ID))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT department_id FROM users WHERE id = ?", UUID.class, maria))
                .isEqualTo(innovation);
    }

    @Test
    void collaboratorCannotCorrectAccounts() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        Cookie mariaSession = login(MARIA);

        mockMvc.perform(put("/users/" + maria).cookie(mariaSession).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Maria da Silva", "52998224725", MARIA, UUID.fromString(GENERAL_ID))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/users/" + maria).cookie(mariaSession)).andExpect(status().isForbidden());
    }

    // ---------- SCRUM-168: filtros da listagem de contas ----------

    @Test
    void listFiltersByRoleAndDepartment() throws Exception {
        UUID maria = register(MARIA, "52998224725", "Maria da Silva", GENERAL_ID);
        UUID joao = register(JOAO, "11144477735", "João Souza", GENERAL_ID);
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, joao);

        mockMvc.perform(get("/users").param("role", "ADMIN").cookie(adminSession))
                .andExpect(jsonPath("$.content[*].id", contains(admin.toString())));
        mockMvc.perform(get("/users").param("role", "HANDLER").cookie(adminSession))
                .andExpect(jsonPath("$.content[*].id", contains(joao.toString())));
        mockMvc.perform(get("/users").param("role", "COLLABORATOR").cookie(adminSession))
                .andExpect(jsonPath("$.content[*].id", contains(maria.toString())));
        mockMvc.perform(get("/users").param("departmentId", INNOVATION_ID).cookie(adminSession))
                .andExpect(jsonPath("$.content[*].id", contains(joao.toString())));
    }

    // ---------- auxiliares ----------

    private UUID departmentId(String name) {
        return jdbc.queryForObject("SELECT id FROM departments WHERE name = ?", UUID.class, name);
    }

    private static String departmentBody(UUID departmentId) {
        return "{ \"departmentId\": \"%s\" }".formatted(departmentId);
    }

    private static String updateBody(String fullName, String cpf, String email, UUID departmentId) {
        return """
                { "fullName": "%s", "cpf": "%s", "email": "%s", "departmentId": "%s" }
                """.formatted(fullName, cpf, email, departmentId);
    }

    private UUID register(String email, String cpf, String name, String departmentId) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "%s", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
                        """.formatted(name, cpf, email, PASSWORD, departmentId)))
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

    private UUID ticket(UUID requester, UUID assignee) {
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, status, category_id, priority_id, requester_id, assignee_id, assigned_at)
                VALUES ('Impressora', 'Sem papel', 'IN_PROGRESS', ?, 2, ?, ?, now()) RETURNING id
                """, UUID.class, category, requester, assignee);
    }
}
