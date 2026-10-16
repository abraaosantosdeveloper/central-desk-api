package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
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
}
