package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.AdminUserDetailResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserUpdateRequest;
import br.com.contabilizape.centraldesk.dto.DepartmentChangeRequest;
import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.PrivilegeChangeRequest;
import br.com.contabilizape.centraldesk.dto.TemporaryPasswordResponse;
import br.com.contabilizape.centraldesk.dto.UserFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
                    (ADMIN, COORDINATOR, HANDLER, COLLABORATOR) e `departmentId`. Sem `status`, as contas \
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
            description = "Só administradores. Além dos campos da listagem, traz o CPF (usado na tela de edição).",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Conta.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDetailResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Usuário não encontrado", value = ProblemExamples.USER_NOT_FOUND)))
    AdminUserDetailResponse get(@Parameter(description = "Id do usuário") UUID id);

    @Operation(
            summary = "Corrige os dados de uma conta",
            description = """
                    Só administradores, e só para contas ativas (inclusive a do próprio administrador). Corrige \
                    nome, CPF, e-mail e departamento de uma vez; mande todos os campos, mesmo os que não mudaram. \
                    CPF e e-mail continuam únicos no sistema. O e-mail é o login: se o de outra pessoa mudar, as \
                    sessões dela são encerradas na hora (o access token em uso passa a receber 401 e as abas \
                    abertas recebem `session.ended` no stream em tempo real). O departamento segue as regras da troca isolada: com pedido de \
                    entrada na Inovação pendente ele não muda, administradores ficam na Inovação e quem sai da \
                    Inovação devolve à fila os chamados em atendimento.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Dados corrigidos.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserDetailResponse.class)))
    @ApiResponse(responseCode = "400", description = "Campo inválido (CPF, e-mail malformado, nome vazio) ou departamento inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Campos inválidos", value = ProblemExamples.INVALID_FIELDS)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = """
                    CPF ou e-mail já usados por outra conta, conta não ativa, pedido de departamento pendente \
                    ou administrador saindo da Inovação.""",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "CPF já cadastrado", value = ProblemExamples.CPF_TAKEN),
                            @ExampleObject(name = "E-mail já cadastrado", value = ProblemExamples.EMAIL_TAKEN)
                    }))
    AdminUserDetailResponse update(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do usuário") UUID id,
                                   AdminUserUpdateRequest request);

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
                    ser decidido antes. Administradores ficam na Inovação: para tirar um deles de lá, remova o \
                    acesso de administrador primeiro.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Departamento alterado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Departamento ausente ou inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Conta não ativa, com pedido de departamento pendente ou administrador saindo da Inovação.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    AdminUserResponse changeDepartment(@Parameter(description = "Id do usuário") UUID id, DepartmentChangeRequest request);

    @Operation(
            summary = "Concede acesso de administrador",
            description = """
                    Só administradores, e só para contas ativas da Inovação (administradores ficam na Inovação). \
                    Vale a partir do próximo login ou renovação da sessão.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Conta agora é de administrador.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "A conta não está ativa, não é da Inovação ou já é de administrador.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    AdminUserResponse grantAdmin(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do usuário") UUID id);

    @Operation(
            summary = "Altera o privilégio de uma conta",
            description = """
                    Só administradores. Níveis exclusivos entre si: `ADMIN`, `COORDINATOR` (coordena o próprio \
                    departamento: vê os chamados que o setor abriu ou recebeu) e `COLLABORATOR`. `ADMIN` só para \
                    contas da Inovação. Ninguém altera o próprio privilégio, a conta precisa estar ativa e o sistema \
                    nunca fica sem administrador ativo. Atender chamados não muda: vem do departamento. \
                    Vale **na hora**: as sessões da pessoa são encerradas (os navegadores dela recebem \
                    `session.ended`), a troca fica na auditoria (`PRIVILEGE_CHANGED`) e a pessoa recebe um e-mail. \
                    Pedir o nível que a conta já tem não muda nada.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Privilégio alterado (ou já era o pedido).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminUserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Nível ausente ou inválido.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Própria conta, conta não ativa, ADMIN fora da Inovação ou último administrador ativo.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Somente Inovação", value = ProblemExamples.ADMIN_ONLY_INNOVATION)))
    AdminUserResponse changePrivilege(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do usuário") UUID id,
                                      PrivilegeChangeRequest request, @Parameter(hidden = true) HttpServletRequest httpRequest);

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

    @Operation(
            summary = "Redefine a senha de uma conta (senha temporária)",
            description = """
                    Só administradores, para contas ativas ou bloqueadas de outras pessoas (é o único jeito de \
                    liberar uma conta bloqueada por 5 tentativas erradas). O sistema gera uma senha temporária de \
                    16 caracteres, que aparece **só nesta resposta**: entregue-a pessoalmente, depois de confirmar \
                    a identidade de quem pediu. Ela vale 24 horas e, no primeiro acesso, a pessoa só consegue \
                    definir uma senha nova (as outras rotas respondem 403 `password-change-required`). \
                    As sessões e os navegadores confiáveis da conta caem na hora, e o dono recebe um e-mail.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Senha redefinida; a conta volta a ACTIVE.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TemporaryPasswordResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conta inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "A própria conta, ou conta desativada, anonimizada ou com cadastro não confirmado.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    TemporaryPasswordResponse resetPassword(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do usuário") UUID id,
                                            @Parameter(hidden = true) HttpServletRequest request);
}
