package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.LoginRequest;
import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Documentação OpenAPI dos endpoints de autenticação (implementada pelo AuthController).
 */
@Tag(name = "Autenticação", description = "Cadastro de conta e login")
public interface AuthApi {

    @Operation(
            summary = "Cadastra um colaborador",
            description = """
                    Endpoint público. A conta é criada como `ACTIVE`.

                    Quem escolhe **Inovação** é cadastrado em **Geral**, com pedido de entrada pendente \
                    (`departmentApprovalPending = true`), e os administradores são avisados. \
                    Só um administrador aprova a entrada na Inovação.""")
    @ApiResponse(
            responseCode = "201",
            description = "Conta criada. A resposta nunca traz a senha nem o hash.",
            headers = @Header(name = "Location", description = "Endereço do usuário criado: /users/{id}",
                    schema = @Schema(type = "string", example = "/users/0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Campos inválidos (lista em `errors`) ou departamento inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Campos inválidos", value = ProblemExamples.INVALID_FIELDS),
                            @ExampleObject(name = "Departamento inexistente", value = ProblemExamples.DEPARTMENT_NOT_FOUND)
                    }))
    @ApiResponse(
            responseCode = "409",
            description = "E-mail ou CPF já cadastrado.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "E-mail já cadastrado", value = ProblemExamples.EMAIL_TAKEN),
                            @ExampleObject(name = "CPF já cadastrado", value = ProblemExamples.CPF_TAKEN)
                    }))
    ResponseEntity<UserResponse> register(UserCreateRequest request);

    @Operation(
            summary = "Entra no sistema",
            description = """
                    Endpoint público. Confere e-mail e senha e devolve o usuário com as permissões.

                    O access token (JWT, 15 minutos) vai **só no cookie** `access_token`, com `HttpOnly`, \
                    `Secure` e `SameSite=Strict`. O navegador o envia sozinho nas próximas requisições, \
                    inclusive aqui no Swagger: depois do login, `GET /users/me` já funciona.""")
    @ApiResponse(
            responseCode = "200",
            description = "Login feito.",
            headers = @Header(name = "Set-Cookie", description = "Cookie access_token com o JWT",
                    schema = @Schema(type = "string",
                            example = "access_token=eyJhbGciOiJIUzI1NiJ9...; Path=/; Max-Age=900; Secure; HttpOnly; SameSite=Strict")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrentUserResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "E-mail ou senha em branco.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Campos em branco", value = ProblemExamples.LOGIN_BLANK_FIELDS)))
    @ApiResponse(
            responseCode = "401",
            description = "E-mail inexistente ou senha errada (mesma resposta nos dois casos).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Credenciais inválidas", value = ProblemExamples.INVALID_CREDENTIALS)))
    @ApiResponse(
            responseCode = "403",
            description = "Senha correta, mas a conta não está ativa.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Conta inativa", value = ProblemExamples.ACCOUNT_NOT_ACTIVE)))
    ResponseEntity<CurrentUserResponse> login(LoginRequest request);
}
