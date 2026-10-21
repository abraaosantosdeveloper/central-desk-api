package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.CategoryResponse;
import br.com.contabilizape.centraldesk.dto.PriorityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * Opções do formulário de chamado (implementada pelo TicketCatalogController).
 */
@Tag(name = "Chamados")
public interface TicketCatalogApi {

    @Operation(summary = "Lista as categorias de chamado", description = "Somente as ativas, ordenadas por nome.",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Categorias.",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = CategoryResponse.class))))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    List<CategoryResponse> categories();

    @Operation(summary = "Lista as prioridades", description = "Da menos à mais urgente (1 a 4).",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Prioridades.",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = PriorityResponse.class))))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    List<PriorityResponse> priorities();
}
