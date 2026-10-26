package br.com.contabilizape.centraldesk.realtime;

import java.util.UUID;

/**
 * As permissões de um usuário mudaram (departamento, perfil de administrador, pedido aprovado). As roles do
 * stream SSE são copiadas do token na conexão, então os streams abertos dele são fechados: o navegador
 * reconecta renovando a sessão e a conexão nova já nasce com as roles certas.
 */
public record UserPermissionsChangedEvent(UUID userId) {
}
