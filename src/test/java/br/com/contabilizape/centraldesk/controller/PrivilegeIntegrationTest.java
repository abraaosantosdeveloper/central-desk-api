package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
import br.com.contabilizape.centraldesk.RecordingEmailSender;
import br.com.contabilizape.centraldesk.email.EmailMessage;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Alterar privilégios" (SCRUM-219, PLANO-REATRIBUICAO-SETOR-E-TODOS-CHAMADOS §3): PUT /users/{id}/privilege
 * com ADMIN, COORDINATOR ou COLLABORATOR, exclusivos entre si.
 */
@IntegrationTest
class PrivilegeIntegrationTest {

    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";
    private static final String PASSWORD = "senhaForte123";
    private static final String ADMIN = "ana.admin@contabilizape.com.br";
    private static final String OTHER_ADMIN = "bruno.admin@contabilizape.com.br";
    private static final String MARIA = "maria.silva@contabilizape.com.br";
    private static final String CARLOS = "carlos.inovacao@contabilizape.com.br";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    private UUID admin;
    private Cookie adminSession;
    private UUID maria;
    private UUID finance;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
        admin = register(ADMIN, "71428793860", "Ana Admin");
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, admin);
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", admin);
        adminSession = login(ADMIN);
        finance = jdbc.queryForObject("SELECT id FROM departments WHERE name = 'Financeiro'", UUID.class);
        maria = register(MARIA, "52998224725", "Maria da Silva");
        jdbc.update("UPDATE users SET department_id = ? WHERE id = ?", finance, maria);
        emails.clear();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM admins WHERE user_id IN (SELECT id FROM users WHERE email IN (?, ?, ?, ?))",
                ADMIN, OTHER_ADMIN, MARIA, CARLOS);
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void adminMakesACollaboratorCoordinatorOfTheOwnDepartment() throws Exception {
        change(maria, "COORDINATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinator").value(true))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.departmentName").value("Financeiro"));

        assertThat(jdbc.queryForObject("SELECT granted_by_id FROM coordinators WHERE user_id = ?", UUID.class, maria))
                .isEqualTo(admin);
        mockMvc.perform(get("/users/" + maria).cookie(adminSession))
                .andExpect(jsonPath("$.coordinator").value(true));
    }

    // PV6: vale na hora; no próximo login a permissão já vem no token
    @Test
    void changeEndsTheSessionsAndTheNextLoginCarriesTheNewRole() throws Exception {
        login(MARIA);

        change(maria, "COORDINATOR").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT sessions_valid_from FROM users WHERE id = ?", Object.class, maria))
                .isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Long.class, maria))
                .isZero();
        mockMvc.perform(get("/users/me").cookie(login(MARIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasItem("COORDINATOR")));
    }

    // PV7
    @Test
    void changeIsAuditedAndThePersonIsNotified() throws Exception {
        change(maria, "COORDINATOR").andExpect(status().isOk());

        String details = jdbc.queryForObject(
                "SELECT details FROM security_events WHERE user_id = ? AND type = 'PRIVILEGE_CHANGED'", String.class, maria);
        assertThat(details).contains("COLLABORATOR -> COORDINATOR").contains(admin.toString());
        assertThat(emails.subjectsTo(MARIA)).containsExactly("Seu nível de acesso mudou");
        assertThat(emails.to(MARIA)).extracting(EmailMessage::html)
                .allMatch(html -> html.contains("Colaborador") && html.contains("Coordenador") && html.contains("Ana Admin"));
    }

    // PV1
    @Test
    void levelsAreExclusive() throws Exception {
        UUID carlos = innovationMember(CARLOS, "39053344705");
        change(carlos, "COORDINATOR").andExpect(status().isOk());

        change(carlos, "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.coordinator").value(false))
                .andExpect(jsonPath("$.ticketHandler").value(true));
        assertThat(count("coordinators", carlos)).isZero();
        assertThat(count("admins", carlos)).isOne();

        change(carlos, "COORDINATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.coordinator").value(true));
        assertThat(count("admins", carlos)).isZero();
        assertThat(count("coordinators", carlos)).isOne();

        // PV5: na Inovação, o colaborador continua atendendo (vem do departamento)
        change(carlos, "COLLABORATOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.coordinator").value(false))
                .andExpect(jsonPath("$.ticketHandler").value(true));
        assertThat(count("coordinators", carlos)).isZero();
    }

    // PV2
    @Test
    void adminOutsideInnovationIsConflict() throws Exception {
        change(maria, "ADMIN")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Inovação")));
        assertThat(count("admins", maria)).isZero();
    }

    // PV3: o token de quem chamou ainda diz admin, mas no banco só sobra a conta alvo
    @Test
    void lastActiveAdminCannotBeDemoted() throws Exception {
        UUID bruno = innovationMember(OTHER_ADMIN, "86288366757");
        jdbc.update("INSERT INTO admins (user_id) VALUES (?)", bruno);
        jdbc.update("DELETE FROM admins WHERE user_id <> ?", bruno);

        change(bruno, "COLLABORATOR").andExpect(status().isConflict());
        assertThat(count("admins", bruno)).isOne();
    }

    // PV4
    @Test
    void nobodyChangesTheOwnPrivilege() throws Exception {
        change(admin, "COLLABORATOR").andExpect(status().isConflict());
        assertThat(count("admins", admin)).isOne();
    }

    @Test
    void inactiveAccountIsConflict() throws Exception {
        jdbc.update("UPDATE users SET status = 'DEACTIVATED', deactivated_at = now() WHERE id = ?", maria);

        change(maria, "COORDINATOR").andExpect(status().isConflict());
    }

    @Test
    void invalidOrMissingLevelIsBadRequest() throws Exception {
        mockMvc.perform(put("/users/" + maria + "/privilege").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"level\": \"DONO\" }"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/users/" + maria + "/privilege").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAccountIsNotFound() throws Exception {
        change(UUID.randomUUID(), "COORDINATOR").andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsChangePrivileges() throws Exception {
        UUID carlos = innovationMember(CARLOS, "39053344705");
        mockMvc.perform(put("/users/" + carlos + "/privilege").cookie(login(MARIA))
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"level\": \"COORDINATOR\" }"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/users/" + carlos + "/privilege")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"level\": \"COORDINATOR\" }"))
                .andExpect(status().isUnauthorized());
    }

    // Perde a coordenação ao trocar de setor (DECISOES-PAINEIS-METRICAS-BI §4, proposta do design)
    @Test
    void changingDepartmentRemovesTheCoordination() throws Exception {
        change(maria, "COORDINATOR").andExpect(status().isOk());

        mockMvc.perform(patch("/users/" + maria + "/department").cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"departmentId\": \"%s\" }".formatted(GENERAL_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinator").value(false));
        assertThat(count("coordinators", maria)).isZero();
    }

    @Test
    void listFiltersCoordinators() throws Exception {
        UUID carlos = innovationMember(CARLOS, "39053344705");
        change(maria, "COORDINATOR").andExpect(status().isOk());

        mockMvc.perform(get("/users").param("role", "COORDINATOR").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(maria.toString())))
                .andExpect(jsonPath("$.content[0].coordinator").value(true));
        mockMvc.perform(get("/users").param("role", "COLLABORATOR").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(maria.toString()))))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(carlos.toString()))));
    }

    // ---------- auxiliares ----------

    private ResultActions change(UUID userId, String level) throws Exception {
        return mockMvc.perform(put("/users/" + userId + "/privilege").cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"level\": \"%s\" }".formatted(level)));
    }

    private long count(String table, UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
    }

    private UUID innovationMember(String email, String cpf) throws Exception {
        UUID id = register(email, cpf, "Carlos Lima");
        jdbc.update("UPDATE users SET department_id = ?::uuid WHERE id = ?", INNOVATION_ID, id);
        return id;
    }

    private UUID register(String email, String cpf, String name) throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        { "fullName": "%s", "cpf": "%s", "email": "%s",
                          "password": "%s", "departmentId": "%s" }
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
