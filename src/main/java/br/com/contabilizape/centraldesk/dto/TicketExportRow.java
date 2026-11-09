package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.TicketStatus;

import java.time.OffsetDateTime;

/**
 * Uma linha da exportação de chamados (SCRUM-XXX). Só o que pode sair do sistema: nada de CPF, e-mail, IDs
 * internos, comentários ou resolução. Montada direto na consulta (TicketRepository), sem carregar as entidades.
 *
 * @param assigneeName nulo quando o chamado está na fila (vira "Em espera" no CSV)
 */
public record TicketExportRow(
        Long number,
        String title,
        String description,
        String requesterName,
        String originDepartment,
        String targetDepartment,
        String assigneeName,
        TicketStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime firstAssignedAt,
        OffsetDateTime closedAt,
        OffsetDateTime updatedAt) {
}
