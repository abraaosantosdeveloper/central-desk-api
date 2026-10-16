package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.dto.DepartmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * Documentação OpenAPI dos endpoints de departamentos (implementada pelo DepartmentController).
 */
@Tag(name = "Departamentos")
public interface DepartmentApi {

    @Operation(
            summary = "Lista os departamentos",
            description = "Endpoint público, usado pelo select do formulário de cadastro. Ordenado por nome.")
    @ApiResponse(
            responseCode = "200",
            description = "Departamentos em ordem alfabética.",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = DepartmentResponse.class))))
    List<DepartmentResponse> list();
}
