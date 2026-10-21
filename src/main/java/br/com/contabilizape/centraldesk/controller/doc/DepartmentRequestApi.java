package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.DepartmentRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/**
 * Documentação OpenAPI da fila de pedidos de departamento (implementada pelo DepartmentRequestController).
 */
@Tag(name = "Pedidos de departamento")
public interface DepartmentRequestApi {

    @Operation(
            summary = "Pedidos pendentes",
            description = """
                    Só administradores. Pedidos de entrada na Inovação aguardando decisão, do mais antigo para \
                    o mais novo. Quem pediu fica no Geral até a decisão.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Pedidos pendentes.",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = DepartmentRequestResponse.class))))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    List<DepartmentRequestResponse> listPending();

    @Operation(
            summary = "Aprova um pedido",
            description = """
                    Só administradores. Quem pediu passa para a Inovação e recebe `TICKET_HANDLER` na próxima \
                    renovação da sessão (em até 15 minutos, ou ao entrar de novo). A conta precisa estar ativa.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Pedido aprovado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepartmentRequestResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Pedido inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "O pedido já foi analisado, ou a conta de quem pediu não está ativa.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Já analisado", value = ProblemExamples.DEPARTMENT_REQUEST_ALREADY_REVIEWED)))
    DepartmentRequestResponse approve(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do pedido") UUID id);

    @Operation(
            summary = "Recusa um pedido",
            description = "Só administradores. Quem pediu continua no Geral e pode pedir de novo depois.",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Pedido recusado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepartmentRequestResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é administrador.", content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Pedido inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "O pedido já foi analisado.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Já analisado", value = ProblemExamples.DEPARTMENT_REQUEST_ALREADY_REVIEWED)))
    DepartmentRequestResponse reject(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do pedido") UUID id);
}
