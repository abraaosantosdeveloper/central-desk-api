package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.AdminUserResponse;
import br.com.contabilizape.centraldesk.dto.DepartmentChangeRequest;
import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.UserFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Documentação OpenAPI do painel de contas do administrador (implementada pelo AdminUserController).
 */
@Tag(name = "Administração de contas")
public interface AdminUserApi {

    @Operation(
            summary = "Lista as contas",
            description = """
                    Só administradores. Ordem alfabética pelo nome; paginado (`size` padrão 20, máximo 100). \
                    Filtros opcionais: `status`, `search` (parte do nome ou do e-mail), `role` \
                    (ADMIN, HANDLER, COLLABORATOR) e `departmentId`. Sem `status`, as contas \
                    **anonimizadas** ficam de fora.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Página de contas.")
    @ApiResponse(
            responseCode = "400",
            description = "Filtro inválido (ex.: `status` desconhecido).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    PageResponse<AdminUserResponse> list(@ParameterObject UserFilter filter, @ParameterObject Pageable pageable);

    @Operation(
            summary = "Consulta uma conta",
            description = "Só administradores.",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Conta.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Usuário não encontrado", value = ProblemExamples.USER_NOT_FOUND)))
    AdminUserResponse get(@Parameter(description = "Id do usuário") UUID id);

    @Operation(
            summary = "Desativa uma conta",
            description = """
                    Só administradores. Bloqueia o login **na hora**: todas as sessões são encerradas e o \
                    access token em uso passa a receber 403. Os chamados em atendimento com a pessoa voltam \
                    para a fila. A conta pode ser reativada ou, depois, anonimizada. \
                    Não é possível desativar a própria conta nem o último administrador ativo.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Conta desativada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "A conta não está ativa, é a do próprio administrador ou é o último administrador ativo.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Própria conta", value = ProblemExamples.SELF_DEACTIVATION),
                            @ExampleObject(name = "Último administrador", value = ProblemExamples.LAST_ADMIN)
                    }))
    AdminUserResponse deactivate(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do usuário") UUID id);

    @Operation(
            summary = "Reativa uma conta",
            description = "Só administradores, e só para contas desativadas. Conta anonimizada não volta.",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Conta reativada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "A conta não está desativada (ativa ou anonimizada).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    AdminUserResponse reactivate(@Parameter(description = "Id do usuário") UUID id);

    @Operation(
            summary = "Troca o departamento de uma conta",
            description = """
                    Só administradores, e só para contas ativas. Mover para a Inovação já vale como aprovação; \
                    quem sai da Inovação devolve à fila os chamados em atendimento. O novo perfil vale a partir \
                    da próxima renovação da sessão. Se houver pedido de entrada na Inovação pendente, ele precisa \
                    ser decidido antes.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Departamento alterado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Departamento ausente ou inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Conta não ativa ou com pedido de departamento pendente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    AdminUserResponse changeDepartment(@Parameter(description = "Id do usuário") UUID id, DepartmentChangeRequest request);

    @Operation(
            summary = "Concede acesso de administrador",
            description = "Só administradores, e só para contas ativas. Vale a partir do próximo login ou renovação da sessão.",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Conta agora é de administrador.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "A conta não está ativa ou já é de administrador.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    AdminUserResponse grantAdmin(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do usuário") UUID id);

    @Operation(
            summary = "Remove acesso de administrador",
            description = "Só administradores. Ninguém remove o próprio acesso, e o sistema nunca fica sem administrador ativo.",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Acesso removido.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Própria conta, conta que não é admin ou último administrador ativo.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Último administrador", value = ProblemExamples.LAST_ADMIN)))
    AdminUserResponse revokeAdmin(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do usuário") UUID id);
}
