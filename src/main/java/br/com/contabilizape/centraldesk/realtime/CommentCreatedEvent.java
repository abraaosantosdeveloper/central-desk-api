package br.com.contabilizape.centraldesk.realtime;

import br.com.contabilizape.centraldesk.dto.CommentResponse;

import java.util.Set;
import java.util.UUID;

/**
 * Comentário gravado. Vai para o solicitante, o responsável e o autor (as outras abas e dispositivos dele).
 */
public record CommentCreatedEvent(CommentResponse comment, Set<UUID> recipientIds) {
}
