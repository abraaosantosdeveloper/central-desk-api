package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.DatabaseCleanup;
import br.com.contabilizape.centraldesk.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /auth/register de ponta a ponta: HTTP → validação → service → Postgres real.
 */
@IntegrationTest
class AuthControllerIntegrationTest {

    // Departamentos fixos criados pela V8
    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";

    private static final String CPF_MARIA = "52998224725";
    private static final String CPF_JOAO = "11144477735";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    // Cada teste começa só com o que as migrations criam (administradores, se houver, são preservados)
    @BeforeEach
    void cleanDatabase() {
        DatabaseCleanup.removeNonAdminUsers(jdbc);
    }

    @Test
    void registersUserInChosenDepartment() throws Exception {
        mockMvc.perform(register("maria.silva@contabilizape.com.br", CPF_MARIA, "senhaForte123", GENERAL_ID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/users/")))
                .andExpect(jsonPath("$.email").value("maria.silva@contabilizape.com.br"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.departmentName").value("Comunidade"))
                .andExpect(jsonPath("$.departmentApprovalPending").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.cpf").doesNotExist());

        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, "maria.silva@contabilizape.com.br");
        assertThat(hash).startsWith("$2a$").isNotEqualTo("senhaForte123");
    }

    @Test
    void innovationRequestStartsInGeneralWithPendingRequest() throws Exception {
        mockMvc.perform(register("joao.souza@contabilizape.com.br", CPF_JOAO, "senhaForte123", INNOVATION_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentName").value("Comunidade"))
                .andExpect(jsonPath("$.departmentApprovalPending").value(true));

        Map<String, Object> request = jdbc.queryForMap("""
                SELECT dr.status, dr.department_id
                FROM department_requests dr
                JOIN users u ON u.id = dr.user_id
                WHERE u.email = ?
                """, "joao.souza@contabilizape.com.br");
        assertThat(request.get("status")).isEqualTo("PENDING");
        assertThat(request.get("department_id").toString()).isEqualTo(INNOVATION_ID);
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        mockMvc.perform(register("maria.silva@contabilizape.com.br", CPF_MARIA, "senhaForte123", GENERAL_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(register("Maria.Silva@contabilizape.com.br", CPF_JOAO, "senhaForte123", GENERAL_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("E-mail já cadastrado"));
    }

    @Test
    void rejectsDuplicateCpf() throws Exception {
        mockMvc.perform(register("maria.silva@contabilizape.com.br", CPF_MARIA, "senhaForte123", GENERAL_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(register("outra.maria@contabilizape.com.br", CPF_MARIA, "senhaForte123", GENERAL_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("CPF já cadastrado"));
    }

    @Test
    void rejectsInvalidFieldsListingEachOne() throws Exception {
        mockMvc.perform(register("maria.silva@contabilizape.com.br", "12345678901", "somenteletras", GENERAL_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cpf").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        assertThat(countUsersWithEmail("maria.silva@contabilizape.com.br")).isZero();
    }

    // Versão genérica: o cadastro é aberto a qualquer domínio de e-mail
    @Test
    void acceptsEmailFromAnyDomain() throws Exception {
        mockMvc.perform(register("maria@gmail.com", CPF_MARIA, "senhaForte123", GENERAL_ID))
                .andExpect(status().isCreated());

        assertThat(countUsersWithEmail("maria@gmail.com")).isOne();
    }

    @Test
    void rejectsUnknownDepartment() throws Exception {
        mockMvc.perform(register("maria.silva@contabilizape.com.br", CPF_MARIA, "senhaForte123",
                        UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{ isto não é json"))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder register(String email, String cpf, String password, String departmentId) {
        String body = """
                {
                  "fullName": "Maria da Silva",
                  "cpf": "%s",
                  "email": "%s",
                  "password": "%s",
                  "departmentId": "%s"
                }
                """.formatted(cpf, email, password, departmentId);
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int countUsersWithEmail(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count == null ? 0 : count;
    }
}
