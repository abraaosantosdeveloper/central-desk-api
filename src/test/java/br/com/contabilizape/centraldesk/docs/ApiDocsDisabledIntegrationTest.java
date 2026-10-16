package br.com.contabilizape.centraldesk.docs;

import br.com.contabilizape.centraldesk.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sem API_DOCS_ENABLED=true (como em produção), a documentação não existe: 404, não 401.
 */
@IntegrationTest
class ApiDocsDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDescriptionIsNotServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiIsNotServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }
}
