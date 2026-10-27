package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.ForgotPasswordRequest;
import br.com.contabilizape.centraldesk.dto.LoginRequest;
import br.com.contabilizape.centraldesk.dto.MessageResponse;
import br.com.contabilizape.centraldesk.dto.PasswordResetRequest;
import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

/**
 * Documentação OpenAPI dos endpoints de autenticação (implementada pelo AuthController).
 */
@Tag(name = "Autenticação", description = "Cadastro de conta, login, renovação da sessão e logout")
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

                    Os tokens vão **só em cookies** `HttpOnly`, `Secure` e `SameSite=Strict`:
                    - `access_token` (JWT, 15 minutos, `Path=/`): enviado em toda requisição da API;
                    - `refresh_token` (7 dias, `Path=/auth`): usado só em `/auth/refresh` e `/auth/logout`.

                    O navegador envia os cookies sozinho, inclusive aqui no Swagger: depois do login, \
                    `GET /users/me` já funciona.""")
    @ApiResponse(
            responseCode = "200",
            description = "Login feito.",
            headers = @Header(name = "Set-Cookie", description = "Dois cookies: access_token (JWT) e refresh_token",
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
            description = """
                    Senha correta, mas a conta não pode entrar. O campo `code` diz o motivo: \
                    `account-blocked` (5 tentativas erradas seguidas; só um administrador libera), \
                    `email-not-verified` (cadastro sem o e-mail confirmado) ou `account-not-active` (desativada).
                    Com a senha errada, a resposta é sempre o 401: só quem sabe a senha descobre o estado da conta.""",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Conta inativa", value = ProblemExamples.ACCOUNT_NOT_ACTIVE)))
    @ApiResponse(
            responseCode = "429",
            description = "Tentativas erradas demais a partir desta rede (20 em 15 minutos). O cabeçalho `Retry-After` diz quantos segundos esperar.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<CurrentUserResponse> login(LoginRequest request, @Parameter(hidden = true) HttpServletRequest httpRequest);

    @Operation(
            summary = "Renova a sessão",
            description = """
                    Endpoint público, chamado quando o access token vence (o `/users/me` e as demais rotas \
                    respondem 401). Usa o cookie `refresh_token` e faz **rotação**: o refresh token atual é \
                    revogado e saem um access token e um refresh token novos. As permissões são recalculadas.

                    Se um refresh token já usado for reapresentado (sinal de roubo), **todas** as sessões \
                    do usuário são encerradas. Por isso, o front-end deve fazer uma renovação por vez.""")
    @ApiResponse(
            responseCode = "200",
            description = "Sessão renovada.",
            headers = @Header(name = "Set-Cookie", description = "Novos cookies access_token e refresh_token",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CurrentUserResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Sem cookie, refresh token desconhecido, vencido ou já usado: é preciso entrar de novo.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Sessão inválida", value = ProblemExamples.INVALID_SESSION)))
    @ApiResponse(
            responseCode = "403",
            description = "A conta foi desativada; todas as sessões dela foram encerradas.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<CurrentUserResponse> refresh(@Parameter(hidden = true) String refreshToken);

    @Operation(
            summary = "Sai do sistema",
            description = """
                    Endpoint público e idempotente: revoga o refresh token da sessão no servidor e apaga \
                    os dois cookies do navegador, com ou sem sessão válida. Sempre responde 204.

                    O access token (JWT) não é guardado no servidor: uma cópia feita antes do logout \
                    valeria até vencer (no máximo 15 minutos), mas não consegue mais renovar a sessão.""")
    @ApiResponse(
            responseCode = "204",
            description = "Sessão encerrada neste navegador.",
            headers = @Header(name = "Set-Cookie", description = "Cookies access_token e refresh_token vazios, expirando na hora",
                    schema = @Schema(type = "string",
                            example = "access_token=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure; HttpOnly; SameSite=Strict")),
            content = @Content)
    ResponseEntity<Void> logout(@Parameter(hidden = true) String refreshToken);

    @Operation(
            summary = "Esqueci minha senha: pede o código por e-mail",
            description = """
                    Endpoint público. A resposta é **sempre** 202 com a mesma mensagem, exista a conta ou não. \
                    Conta ativa recebe um código de 6 dígitos (vale 10 minutos); conta bloqueada recebe um aviso \
                    para procurar a equipe de Inovação (só um administrador libera); nos outros casos, nada é enviado. \
                    Um novo pedido em menos de 60 segundos não envia outro código.""")
    @ApiResponse(responseCode = "202", description = "Pedido recebido.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "400", description = "E-mail em branco.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Pedidos demais a partir desta rede (`Retry-After`).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<MessageResponse> forgotPassword(ForgotPasswordRequest request,
                                                   @Parameter(hidden = true) HttpServletRequest httpRequest);

    @Operation(
            summary = "Esqueci minha senha: redefine com o código",
            description = """
                    Endpoint público. Confere o código e grava a senha nova (mesmas regras do cadastro). \
                    Todas as sessões e navegadores confiáveis da conta caem, e o dono recebe um e-mail. \
                    Código errado não conta para o bloqueio da conta, mas cada código aceita só 5 tentativas.""")
    @ApiResponse(responseCode = "204", description = "Senha redefinida. Entre de novo com a senha nova.")
    @ApiResponse(responseCode = "400", description = """
            Código inválido, vencido ou já usado (`errors.code`, sem dizer qual), ou senha fora das regras (`errors.newPassword`).""",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<Void> resetPassword(PasswordResetRequest request, @Parameter(hidden = true) HttpServletRequest httpRequest);
}
