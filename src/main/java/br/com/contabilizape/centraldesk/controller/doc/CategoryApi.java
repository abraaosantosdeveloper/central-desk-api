package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.CategoryRequest;
import br.com.contabilizape.centraldesk.dto.CategoryResponse;
import br.com.contabilizape.centraldesk.dto.CategoryUsageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * Gestão de categorias pelo administrador (implementada pelo CategoryController).
 */
@Tag(name = "Administração")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public interface CategoryApi {

    @Operation(summary = "Cria uma categoria", description = "Somente administradores. O nome é único (sem diferenciar maiúsculas).")
    @ApiResponse(responseCode = "201", description = "Categoria criada.",
            content = @Content(schema = @Schema(implementation = CategoryResponse.class)))
    @ApiResponse(responseCode = "400", description = "Nome vazio ou cor fora do formato #RRGGBB.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Não é administrador.", content = @Content)
    @ApiResponse(responseCode = "409", description = "Já existe uma categoria com esse nome.", content = @Content)
    CategoryResponse create(CategoryRequest request);

    @Operation(summary = "Edita nome e cor de uma categoria", description = "Somente administradores.")
    @ApiResponse(responseCode = "200", description = "Categoria atualizada.",
            content = @Content(schema = @Schema(implementation = CategoryResponse.class)))
    @ApiResponse(responseCode = "400", description = "Dados inválidos.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Categoria inexistente ou removida.", content = @Content)
    @ApiResponse(responseCode = "409", description = "Já existe uma categoria com esse nome.", content = @Content)
    CategoryResponse update(UUID id, CategoryRequest request);

    @Operation(summary = "Remove uma categoria",
            description = "Somente administradores. A categoria é arquivada: some do formulário de novo chamado, "
                    + "mas os chamados que já a usam continuam com ela.")
    @ApiResponse(responseCode = "204", description = "Categoria removida.")
    @ApiResponse(responseCode = "403", description = "Não é administrador.", content = @Content)
    @ApiResponse(responseCode = "404", description = "Categoria inexistente ou já removida.", content = @Content)
    void archive(UUID id);

    @Operation(summary = "Quantidade de chamados por categoria ativa", description = "Somente administradores.")
    @ApiResponse(responseCode = "200", description = "Uso das categorias.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CategoryUsageResponse.class))))
    @ApiResponse(responseCode = "403", description = "Não é administrador.", content = @Content)
    List<CategoryUsageResponse> usage();
}
