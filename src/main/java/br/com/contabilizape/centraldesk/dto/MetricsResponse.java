package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Painel de métricas (SCRUM-216). Os chamados do período são os ABERTOS nele (created_at); o período anterior
 * tem a mesma duração e vem logo antes. Tempos em segundos (null quando não há chamado para a média).
 */
@Schema(description = "Métricas de atendimento no período, com o período anterior para comparação")
public record MetricsResponse(
    @Schema(description = "Período consultado") Period period,
    @Schema(description = "Período anterior, de mesma duração") Period previousPeriod,
    @Schema(description = "Setor de origem aplicado: o do coordenador (travado) ou o escolhido pelo administrador; "
            + "null = todos os setores") Sector sector,
    @Schema(description = "true = o setor é do coordenador e não pode ser trocado") boolean sectorLocked,
    Summary summary,
    @Schema(description = "O mesmo resumo no período anterior") Summary previousSummary,
    @Schema(description = "Chamados abertos ou em andamento neste momento, com os mesmos filtros de setor, categoria "
            + "e prioridade, sem recorte de datas") Live now,
    @Schema(description = "Um item por dia do período, inclusive dias sem movimento") List<Day> perDay,
    List<PriorityShare> byPriority,
    List<GroupStat> byCategory,
    @Schema(description = "Vazio para coordenadores (o setor já é um só)") List<GroupStat> byOriginDepartment,
    List<AssigneeStat> byAssignee,
    @Schema(description = "Até 5 chamados sem responsável, do mais antigo para o mais novo") List<OldestTicket> oldestUnassigned,
    @Schema(description = "Quando as métricas foram calculadas") OffsetDateTime generatedAt
) {

    public record Period(LocalDate from, LocalDate to) {
    }

    public record Sector(UUID id, String name) {
    }

    @Schema(description = "Chamados abertos no período e o que aconteceu com eles")
    public record Summary(
        @Schema(description = "Chamados abertos no período") long total,
        @Schema(description = "Quantos já foram assumidos alguma vez (first_assigned_at)") long taken,
        @Schema(description = "Quantos estão encerrados agora") long closed,
        @Schema(description = "Reaberturas feitas no período, de qualquer chamado do escopo") long reopened,
        @Schema(description = "Média da abertura até ser assumido pela primeira vez, em segundos") Long avgResponseSeconds,
        @Schema(description = "Média da abertura até o encerramento, em segundos") Long avgResolutionSeconds
    ) {
    }

    public record Live(long open, long unassigned) {
    }

    @Schema(description = "Movimento de um dia: abertos (created_at) e encerrados (closed_at)")
    public record Day(LocalDate date, long opened, long closed) {
    }

    public record PriorityShare(short id, String label, long count) {
    }

    @Schema(description = "Quantidade de chamados do período e resolução média, por categoria ou por setor")
    public record GroupStat(UUID id, String label, long count, Long avgResolutionSeconds) {
    }

    public record AssigneeStat(UUID id, String name, long inProgress, long taken, long closed,
                               Long avgResponseSeconds, Long avgResolutionSeconds) {
    }

    public record OldestTicket(UUID id, long number, String title, String requesterName, String requesterDepartment,
                               OffsetDateTime createdAt) {
    }
}
