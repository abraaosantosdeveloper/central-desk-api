package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.AccountDeletionRequest;
import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.PasswordChangeRequest;
import br.com.contabilizape.centraldesk.dto.UserProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Documentação OpenAPI dos endpoints de usuários (implementada pelo UserController).
 */
@Tag(name = "Usuários")
public interface UserApi {

    @Operation(
            summary = "Dados do usuário logado",
            description = "Exige o cookie `access_token` emitido pelo login. As permissões são as gravadas no token.",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(
            responseCode = "200",
            description = "Usuário logado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrentUserResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Sem cookie, ou token inválido ou vencido: é preciso entrar de novo. Sem corpo; "
                    + "o cabeçalho WWW-Authenticate indica o motivo.",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "A conta foi desativada depois do login.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    CurrentUserResponse me(@Parameter(hidden = true) Jwt jwt);

    @Operation(
            summary = "Edita o próprio perfil",
            description = """
                    Só o **nome** é editável. CPF, e-mail, departamento e permissões não mudam por aqui; \
                    outros campos enviados no JSON são ignorados.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(
            responseCode = "200",
            description = "Perfil atualizado. Mesmo formato do `GET /users/me`.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrentUserResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Nome em branco ou com mais de 150 caracteres.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Nome em branco", value = ProblemExamples.PROFILE_INVALID_FIELDS)))
    @ApiResponse(
            responseCode = "401",
            description = "Sem cookie, token inválido ou vencido, ou usuário que não existe mais.",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "A conta não está ativa.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    CurrentUserResponse updateProfile(@Parameter(hidden = true) Jwt jwt, UserProfileUpdateRequest request);

    @Operation(
            summary = "Troca a própria senha",
            description = """
                    Exige a senha atual. A nova segue as regras do cadastro (8 a 72 caracteres, com letra e número) \
                    e precisa ser diferente da atual.

                    Ao trocar, **todas as sessões** do usuário são encerradas na hora: os outros dispositivos não \
                    renovam mais e o access token deles passa a receber 401 (as abas abertas recebem \
                    `session.ended` no stream em tempo real). Esta sessão recebe cookies novos na própria resposta \
                    e continua logada.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(
            responseCode = "204",
            description = "Senha trocada.",
            headers = @Header(name = "Set-Cookie", description = "Novos cookies access_token e refresh_token desta sessão",
                    schema = @Schema(type = "string")),
            content = @Content)
    @ApiResponse(
            responseCode = "400",
            description = "Senha atual errada (`errors.currentPassword`), senha nova fraca ou igual à atual "
                    + "(`errors.newPassword`). Não é 401, para não ser confundido com sessão vencida.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Senha atual errada", value = ProblemExamples.CURRENT_PASSWORD_MISMATCH),
                            @ExampleObject(name = "Senha nova fraca", value = ProblemExamples.WEAK_NEW_PASSWORD),
                            @ExampleObject(name = "Senha nova igual à atual", value = ProblemExamples.NEW_PASSWORD_SAME_AS_CURRENT)
                    }))
    @ApiResponse(
            responseCode = "401",
            description = "Sem cookie, token inválido ou vencido, ou usuário que não existe mais.",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "A conta não está ativa.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<Void> changePassword(@Parameter(hidden = true) Jwt jwt, PasswordChangeRequest request);

    @Operation(
            summary = "Encerra a própria conta",
            description = """
                    Anonimiza a conta (LGPD), sem exclusão física: nome vira "Usuário removido", CPF é apagado, \
                    o e-mail é substituído e ninguém mais entra com ela. Os chamados abertos pelo usuário ficam \
                    no histórico; os que estavam em atendimento com ele voltam para a fila. Todas as sessões \
                    são encerradas e as notificações dele são apagadas. Exige a senha atual.

                    O último administrador ativo não pode encerrar a própria conta.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(
            responseCode = "204",
            description = "Conta encerrada.",
            headers = @Header(name = "Set-Cookie", description = "Cookies access_token e refresh_token vazios, expirando na hora",
                    schema = @Schema(type = "string")),
            content = @Content)
    @ApiResponse(
            responseCode = "400",
            description = "Senha em branco ou errada (`errors.password`).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Senha errada", value = ProblemExamples.DELETE_ACCOUNT_WRONG_PASSWORD)))
    @ApiResponse(
            responseCode = "401",
            description = "Sem cookie, token inválido ou vencido, ou usuário que não existe mais.",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "A conta não está ativa.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "É a última conta de administrador ativa.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Último administrador", value = ProblemExamples.LAST_ADMIN)))
    ResponseEntity<Void> deleteOwnAccount(@Parameter(hidden = true) Jwt jwt, AccountDeletionRequest request);

    @Operation(
            summary = "Anonimiza uma conta (administrador)",
            description = """
                    Só para administradores, e só para contas **já desativadas**. Mesmo efeito do encerramento \
                    pelo próprio usuário: dados pessoais apagados, sessões encerradas, chamados em atendimento \
                    de volta à fila e notificações apagadas.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "204", description = "Conta anonimizada.", content = @Content)
    @ApiResponse(
            responseCode = "401",
            description = "Sem cookie, token inválido ou vencido.",
            content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Quem chamou não é administrador.",
            content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Usuário não encontrado.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Usuário não encontrado", value = ProblemExamples.USER_NOT_FOUND)))
    @ApiResponse(
            responseCode = "409",
            description = "A conta não está desativada (ativa ou já anonimizada), ou é o último administrador ativo.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Conta não desativada", value = ProblemExamples.ACCOUNT_NOT_DEACTIVATED)))
    ResponseEntity<Void> anonymize(@Parameter(description = "Id do usuário") UUID id);
}
