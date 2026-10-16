package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.Priority;
import io.swagger.v3.oas.annotations.media.Schema;

public record PriorityResponse(
    @Schema(example = "2") Short id,
    @Schema(example = "Média") String label
) {

    public static PriorityResponse from(Priority priority) {
        return new PriorityResponse(priority.getId(), priority.getLabel());
    }
}
