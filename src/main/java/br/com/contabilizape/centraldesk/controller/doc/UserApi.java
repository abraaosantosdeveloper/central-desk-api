package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
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

                    Ao trocar, **todas as sessões** do usuário são encerradas (os outros dispositivos não renovam mais) \
                    e esta sessão recebe cookies novos na própria resposta, continuando logada.""",
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
}
