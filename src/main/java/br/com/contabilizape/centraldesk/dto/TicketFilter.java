package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Filtros opcionais das listagens de chamados (query string). Campo ausente = sem filtro.
 */
public record TicketFilter(
    @Schema(description = "Status do chamado", example = "OPEN")
    TicketStatus status,

    @Schema(description = "Categoria (ids em GET /categories)")
    UUID categoryId,

    @Schema(description = "Prioridade (ids em GET /priorities)", example = "2")
    Short priorityId
) {}
