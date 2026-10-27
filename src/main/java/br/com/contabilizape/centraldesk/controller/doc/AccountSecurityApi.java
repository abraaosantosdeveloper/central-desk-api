package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.MessageResponse;
import br.com.contabilizape.centraldesk.dto.TrustedDeviceResponse;
import br.com.contabilizape.centraldesk.dto.TwoFactorStatusResponse;
import br.com.contabilizape.centraldesk.dto.TwoFactorUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/**
 * Documentação OpenAPI da seção "Segurança" de Meu perfil (implementada pelo AccountSecurityController).
 */
@Tag(name = "Segurança da conta", description = "Verificação em duas etapas, navegadores confiáveis e código para trocar a senha")
public interface AccountSecurityApi {

    @Operation(summary = "Situação da verificação em duas etapas",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Situação atual.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TwoFactorStatusResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    TwoFactorStatusResponse twoFactor(@Parameter(hidden = true) Jwt jwt);

    @Operation(summary = "Envia o código para desligar a verificação em duas etapas",
            description = "Não vale para administradores (para eles a verificação é obrigatória).",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "202", description = "Código enviado por e-mail.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "409", description = "Administrador.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Pedido cedo demais (`Retry-After`).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<MessageResponse> sendTwoFactorDisableCode(@Parameter(hidden = true) Jwt jwt,
                                                             @Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "Liga ou desliga a verificação em duas etapas",
            description = """
                    Ligar não pede nada. Desligar pede a senha atual e o código de `POST /users/me/two-factor/code`; \\
                    os navegadores confiáveis são esquecidos e o dono recebe um e-mail. Administradores não desligam.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Situação nova.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TwoFactorStatusResponse.class)))
    @ApiResponse(responseCode = "400", description = "Senha atual errada (`errors.currentPassword`) ou código inválido (`errors.code`).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Administrador tentando desligar.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    TwoFactorStatusResponse updateTwoFactor(@Parameter(hidden = true) Jwt jwt, TwoFactorUpdateRequest body,
                                            @Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "Envia o código para trocar a senha",
            description = "Com a verificação em duas etapas ligada, `PATCH /users/me/password` pede este código.",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "202", description = "Código enviado por e-mail.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "429", description = "Pedido cedo demais (`Retry-After`).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<MessageResponse> sendPasswordChangeCode(@Parameter(hidden = true) Jwt jwt,
                                                           @Parameter(hidden = true) HttpServletRequest request);

    @Operation(summary = "Lista os navegadores confiáveis", security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Navegadores dispensados do código, do mais novo para o mais antigo.",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = TrustedDeviceResponse.class))))
    List<TrustedDeviceResponse> trustedDevices(@Parameter(hidden = true) Jwt jwt);

    @Operation(summary = "Remove um navegador confiável", security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "204", description = "Removido (o próximo login nele pede o código).")
    ResponseEntity<Void> removeTrustedDevice(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do navegador") UUID id);

    @Operation(summary = "Remove todos os navegadores confiáveis", security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "204", description = "Removidos.")
    ResponseEntity<Void> removeAllTrustedDevices(@Parameter(hidden = true) Jwt jwt);
}
