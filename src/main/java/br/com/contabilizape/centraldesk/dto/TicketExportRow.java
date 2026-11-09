package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.TicketStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um chamado na exportação em planilha (SCRUM-222): todos os campos da tabela {@code tickets}, com as chaves
 * estrangeiras trocadas pelo nome (categoria, prioridade, setores e pessoas). Ficam de fora a conversa
 * (comentários) e os dados pessoais das contas (CPF, e-mail). Montada direto na consulta (TicketRepository).
 *
 * Campos opcionais vêm nulos e viram célula vazia na planilha.
 */
public record TicketExportRow(
        UUID id,
        Long number,
        String title,
        String description,
        TicketStatus status,
        String category,
        String priority,
        String requesterName,
        String originDepartment,
        String targetDepartment,
        String assigneeName,
        OffsetDateTime assignedAt,
        OffsetDateTime firstAssignedAt,
        String closedByName,
        OffsetDateTime closedAt,
        String resolution,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
