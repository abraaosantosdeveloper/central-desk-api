package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /departments de ponta a ponta.
 */
@IntegrationTest
class DepartmentControllerIntegrationTest {

    // Departamentos fixos criados pela V8
    private static final String GENERAL_ID = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016";
    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeExtraDepartments() {
        jdbc.update("DELETE FROM departments WHERE id NOT IN (?::uuid, ?::uuid)", GENERAL_ID, INNOVATION_ID);
    }

    @Test
    void listsSeedDepartmentsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(GENERAL_ID))
                .andExpect(jsonPath("$[0].name").value("Geral"))
                .andExpect(jsonPath("$[1].id").value(INNOVATION_ID))
                .andExpect(jsonPath("$[1].name").value("Inovação"));
    }

    @Test
    void ordersDepartmentsByName() throws Exception {
        jdbc.update("INSERT INTO departments (name) VALUES ('Recursos Humanos'), ('Financeiro')");

        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(
                        org.hamcrest.Matchers.contains("Financeiro", "Geral", "Inovação", "Recursos Humanos")));
    }

    @Test
    void exposesOnlyIdAndName() throws Exception {
        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }

    @Test
    void onlyGetIsPublic() throws Exception {
        mockMvc.perform(post("/departments").contentType("application/json").content("{\"name\":\"Novo\"}"))
                // 401/403 = bloqueado pela segurança. Se a rota estivesse liberada, viria 405 (método não suportado)
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }
}
