package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.Category;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CategoryResponse(
    UUID id,
    @Schema(example = "Hardware") String label,
    @Schema(example = "#EF4444") String color
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getLabel(), category.getColor());
    }
}
