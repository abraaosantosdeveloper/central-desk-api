package br.com.contabilizape.centraldesk.docs;

import br.com.contabilizape.centraldesk.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documentação OpenAPI ligada (como no ambiente de desenvolvimento, com API_DOCS_ENABLED=true).
 */
@IntegrationTest
@TestPropertySource(properties = "API_DOCS_ENABLED=true")
class ApiDocsIntegrationTest {

    private static final String REGISTER = "$.paths['/auth/register'].post";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesOpenApiDescriptionWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CentralDesk API"))
                .andExpect(jsonPath("$.paths['/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/departments'].get").exists());
    }

    @Test
    void servesSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void documentsRegisterResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(REGISTER + ".responses['201']").exists())
                .andExpect(jsonPath(REGISTER + ".responses['201'].headers.Location").exists())
                .andExpect(jsonPath(REGISTER + ".responses['400'].content['" + PROBLEM_JSON + "']").exists())
                .andExpect(jsonPath(REGISTER + ".responses['409'].content['" + PROBLEM_JSON + "']").exists());
    }

    @Test
    void documentsDepartmentsResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/departments'].get.responses['200']").exists());
    }

    @Test
    void documentsLoginResponses() throws Exception {
        String login = "$.paths['/auth/login'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(login + ".responses['200'].headers['Set-Cookie']").exists())
                .andExpect(jsonPath(login + ".responses['400'].content['" + PROBLEM_JSON + "']").exists())
                .andExpect(jsonPath(login + ".responses['401'].content['" + PROBLEM_JSON + "']").exists())
                .andExpect(jsonPath(login + ".responses['403'].content['" + PROBLEM_JSON + "']").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password.writeOnly").value(true));
    }

    @Test
    void documentsRefresh() throws Exception {
        String refresh = "$.paths['/auth/refresh'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(refresh + ".responses['200'].headers['Set-Cookie']").exists())
                .andExpect(jsonPath(refresh + ".responses['401'].content['" + PROBLEM_JSON + "']").exists())
                .andExpect(jsonPath(refresh + ".responses['403'].content['" + PROBLEM_JSON + "']").exists());
    }

    @Test
    void documentsTicketOpening() throws Exception {
        String open = "$.paths['/tickets'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(open + ".responses['201'].headers.Location").exists())
                .andExpect(jsonPath(open + ".responses['400'].content['" + PROBLEM_JSON + "']").exists())
                .andExpect(jsonPath(open + ".responses['401']").exists())
                .andExpect(jsonPath(open + ".security[0].cookieAuth").isArray())
                .andExpect(jsonPath("$.paths['/tickets/{id}'].put.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/tickets/{id}'].put.responses['403'].content['" + PROBLEM_JSON + "']").exists())
                .andExpect(jsonPath("$.paths['/tickets/{id}'].put.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/tickets/{id}'].put.responses['409'].content['" + PROBLEM_JSON + "']").exists())
                .andExpect(jsonPath("$.paths['/categories'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/priorities'].get.responses['200']").exists());
    }

    @Test
    void documentsLogout() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.responses['204'].headers['Set-Cookie']").exists());
    }

    @Test
    void documentsCurrentUserEndpointAsProtectedByTheCookie() throws Exception {
        String me = "$.paths['/users/me'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(me + ".responses['200']").exists())
                .andExpect(jsonPath(me + ".responses['401']").exists())
                .andExpect(jsonPath(me + ".responses['403']").exists())
                .andExpect(jsonPath(me + ".security[0].cookieAuth").isArray())
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.name").value("access_token"));
    }

    @Test
    void documentsProfileEditAndPasswordChange() throws Exception {
        String put = "$.paths['/users/me'].put";
        String patch = "$.paths['/users/me/password'].patch";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(put + ".responses['200']").exists())
                .andExpect(jsonPath(put + ".responses['400']").exists())
                .andExpect(jsonPath(put + ".responses['401']").exists())
                .andExpect(jsonPath(put + ".responses['403']").exists())
                .andExpect(jsonPath(put + ".security[0].cookieAuth").isArray())
                .andExpect(jsonPath(patch + ".responses['204'].headers['Set-Cookie']").exists())
                .andExpect(jsonPath(patch + ".responses['400']").exists())
                .andExpect(jsonPath(patch + ".responses['401']").exists())
                .andExpect(jsonPath(patch + ".responses['403']").exists())
                .andExpect(jsonPath(patch + ".security[0].cookieAuth").isArray())
                .andExpect(jsonPath("$.components.schemas.PasswordChangeRequest.properties.currentPassword.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.PasswordChangeRequest.properties.newPassword.writeOnly").value(true));
    }

    @Test
    void documentsAccountAnonymization() throws Exception {
        String delete = "$.paths['/users/me'].delete";
        String anonymize = "$.paths['/users/{id}/anonymize'].patch";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(delete + ".responses['204'].headers['Set-Cookie']").exists())
                .andExpect(jsonPath(delete + ".responses['400']").exists())
                .andExpect(jsonPath(delete + ".responses['401']").exists())
                .andExpect(jsonPath(delete + ".responses['403']").exists())
                .andExpect(jsonPath(delete + ".responses['409']").exists())
                .andExpect(jsonPath(anonymize + ".responses['204']").exists())
                .andExpect(jsonPath(anonymize + ".responses['401']").exists())
                .andExpect(jsonPath(anonymize + ".responses['403']").exists())
                .andExpect(jsonPath(anonymize + ".responses['404']").exists())
                .andExpect(jsonPath(anonymize + ".responses['409']").exists())
                .andExpect(jsonPath(anonymize + ".security[0].cookieAuth").isArray());
    }

    @Test
    void passwordIsWriteOnlyAndNeverPartOfTheResponse() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.UserCreateRequest.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.fullName").exists())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserResponse.properties.passwordHash").doesNotExist());
    }
}
