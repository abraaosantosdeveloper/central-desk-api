package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros do painel de métricas (GET /dashboard/metrics, SCRUM-216). Campo ausente = sem filtro; sem período,
 * valem os últimos 30 dias. O escopo (administrador: tudo; coordenador: o próprio setor) é imposto no servidor.
 */
public record MetricsFilter(
    @Schema(description = "Primeiro dia do período (inclusive); padrão: 29 dias antes de `to`", example = "2026-10-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate from,

    @Schema(description = "Último dia do período (inclusive); padrão: hoje", example = "2026-10-28")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate to,

    @Schema(description = "Setor de origem (só administradores; ignorado para coordenadores)")
    UUID originDepartmentId,

    @Schema(description = "Categoria (ids em GET /categories)")
    UUID categoryId,

    @Schema(description = "Prioridade (ids em GET /priorities)", example = "2")
    Short priorityId
) {
}
