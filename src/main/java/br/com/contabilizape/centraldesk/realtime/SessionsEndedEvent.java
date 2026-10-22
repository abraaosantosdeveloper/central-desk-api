package br.com.contabilizape.centraldesk.realtime;

import java.util.UUID;

/**
 * As sessões de um usuário foram encerradas (e-mail de login trocado pelo admin, troca de senha).
 * Os streams SSE abertos dele recebem "session.ended" e são fechados: a tela confere a sessão
 * na hora, em vez de esperar a próxima requisição.
 */
public record SessionsEndedEvent(UUID userId) {
}
