package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;

/**
 * Resultado do login: o usuário vai no corpo da resposta e o token, só no cookie.
 */
public record AuthenticatedSession(CurrentUserResponse user, String accessToken) {
}
