package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.TicketCreateRequest;
import br.com.contabilizape.centraldesk.dto.TicketResponse;
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
 * Documentação OpenAPI dos endpoints de chamados (implementada pelo TicketController).
 */
@Tag(name = "Chamados")
public interface TicketApi {

    @Operation(
            summary = "Abre um chamado",
            description = """
                    Qualquer usuário logado e ativo pode abrir. O solicitante é sempre quem está logado, \
                    e o chamado nasce `OPEN`, sem responsável.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(
            responseCode = "201",
            description = "Chamado aberto.",
            headers = @Header(name = "Location", description = "Endereço do chamado: /tickets/{id}",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TicketResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Campos inválidos (lista em `errors`), categoria ou prioridade inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Campos inválidos", value = ProblemExamples.TICKET_INVALID_FIELDS),
                            @ExampleObject(name = "Categoria inexistente", value = ProblemExamples.CATEGORY_NOT_FOUND)
                    }))
    @ApiResponse(responseCode = "401", description = "Sem sessão: é preciso entrar (ou renovar a sessão).", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "A conta foi desativada.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<TicketResponse> open(@Parameter(hidden = true) Jwt jwt, TicketCreateRequest request);
}
