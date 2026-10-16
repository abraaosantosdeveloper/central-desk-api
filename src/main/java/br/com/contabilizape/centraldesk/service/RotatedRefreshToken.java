package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.model.User;

/**
 * Resultado da rotação: o dono da sessão e o novo refresh token (valor em claro, só para o cookie).
 */
public record RotatedRefreshToken(User user, String rawToken) {
}
