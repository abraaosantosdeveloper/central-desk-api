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
    Short priorityId,

    @Schema(description = "Busca: parte do título (sem diferenciar maiúsculas) ou o número do chamado (#1A2B3C4D)",
            example = "impressora")
    String q,

    @Schema(description = "Departamento de quem abriu o chamado (ids em GET /departments)")
    UUID departmentId,

    @Schema(description = "Ordenação: RECENT (mais recentes primeiro, padrão) ou PRIORITY "
            + "(mais urgentes primeiro; empate, o mais antigo primeiro)", example = "PRIORITY")
    TicketOrder orderBy
) {

    public enum TicketOrder { RECENT, PRIORITY }
}
