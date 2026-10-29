package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Página de \"Todos os chamados\" com os contadores das abas de status")
public record TicketOverviewResponse(
    List<TicketSummaryResponse> content,
    @Schema(description = "Número da página, começando em 0") int page,
    @Schema(description = "Itens por página") int size,
    long totalElements,
    int totalPages,
    @Schema(description = "Quantos chamados há em cada status com os mesmos filtros, menos o de status")
    StatusCounts statusCounts
) {

    // Nomes iguais aos de TicketStatus, para o front indexar as abas pelo próprio status
    @Schema(description = "Contadores das abas Todos / Abertos / Em andamento / Encerrados")
    public record StatusCounts(long all, long OPEN, long IN_PROGRESS, long CLOSED) {
    }
}
