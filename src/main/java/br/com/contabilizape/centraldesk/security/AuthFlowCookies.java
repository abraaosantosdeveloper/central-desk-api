package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.service.LoginChallengeService;
import br.com.contabilizape.centraldesk.service.TrustedDeviceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookies da verificação em duas etapas, com a mesma proteção do refresh token (HttpOnly, Secure,
 * SameSite=Strict, Path=/auth: só vão para as rotas de login, nunca para o resto da API).
 *
 * - tfa_challenge: o desafio entre a senha certa e o código (5 minutos);
 * - trusted_device: o navegador dispensado do código (30 dias fixos).
 */
@Component
public class AuthFlowCookies {

    public static final String CHALLENGE = "tfa_challenge";
    public static final String TRUSTED_DEVICE = "trusted_device";
    private static final String PATH = "/auth";

    private final boolean secure;

    public AuthFlowCookies(@Value("${app.security.cookie.secure}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie challenge(String rawToken) {
        return cookie(CHALLENGE, rawToken, LoginChallengeService.VALIDITY);
    }

    public ResponseCookie clearChallenge() {
        return cookie(CHALLENGE, "", Duration.ZERO);
    }

    public ResponseCookie trustedDevice(String rawToken) {
        return cookie(TRUSTED_DEVICE, rawToken, TrustedDeviceService.VALIDITY);
    }

    private ResponseCookie cookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
