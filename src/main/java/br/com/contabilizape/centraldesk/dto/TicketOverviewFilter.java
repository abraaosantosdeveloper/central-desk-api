package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros de "Todos os chamados" (GET /tickets/overview, PLANO-REATRIBUICAO-SETOR-E-TODOS-CHAMADOS §2.2).
 * Campo ausente = sem filtro. O escopo (admin: tudo; coordenador: o próprio setor) é imposto no servidor.
 */
public record TicketOverviewFilter(
    @Schema(description = "Status; ausente = abertos, em andamento e encerrados juntos", example = "OPEN")
    TicketStatus status,

    @Schema(description = "Setor de origem (só administradores; ignorado para coordenadores)")
    UUID originDepartmentId,

    @Schema(description = "Setor de destino (só administradores; ignorado para coordenadores)")
    UUID targetDepartmentId,

    @Schema(description = "Coordenadores: ORIGIN (o setor abriu), TARGET (o setor recebeu) ou BOTH (padrão). "
            + "Ignorado para administradores", example = "BOTH")
    SectorRelation relation,

    @Schema(description = "Categoria (ids em GET /categories)")
    UUID categoryId,

    @Schema(description = "Prioridade (ids em GET /priorities)", example = "2")
    Short priorityId,

    @Schema(description = "Responsável pelo chamado (ids em GET /tickets/overview/assignees)")
    UUID assigneeId,

    @Schema(description = "true = só chamados sem responsável; ignorado quando `assigneeId` vem junto", example = "true")
    Boolean unassigned,

    @Schema(description = "Busca: parte do título, número do chamado (#123 ou início do id) ou parte do nome de quem abriu. "
            + "Comentários não entram (são cifrados)", example = "impressora")
    String q,

    @Schema(description = "Abertos a partir deste dia (inclusive)", example = "2026-10-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate from,

    @Schema(description = "Abertos até este dia (inclusive)", example = "2026-10-28")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate to,

    @Schema(description = "Ordenação: RECENT (mais recentes primeiro, padrão), PRIORITY (mais urgentes; empate, o mais "
            + "antigo) ou UPDATED (atualizados há menos tempo)", example = "RECENT")
    OverviewOrder orderBy
) {

    public enum SectorRelation { ORIGIN, TARGET, BOTH }

    public enum OverviewOrder { RECENT, PRIORITY, UPDATED }
}
