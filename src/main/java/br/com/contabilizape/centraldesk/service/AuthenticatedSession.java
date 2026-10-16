package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;

/**
 * Resultado do login e da renovação: o usuário vai no corpo da resposta; os tokens, só nos cookies.
 */
public record AuthenticatedSession(CurrentUserResponse user, String accessToken, String refreshToken) {
}
