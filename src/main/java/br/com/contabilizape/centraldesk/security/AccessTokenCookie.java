package br.com.contabilizape.centraldesk.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Monta o cookie do access token: HttpOnly (o JavaScript não lê), Secure (só HTTPS),
 * SameSite=Strict (não vai em requisições vindas de outros sites) e com a mesma validade do token.
 */
@Component
public class AccessTokenCookie {

    public static final String NAME = "access_token";

    private final TokenService tokenService;
    private final boolean secure;

    public AccessTokenCookie(TokenService tokenService, @Value("${app.security.cookie.secure}") boolean secure) {
        this.tokenService = tokenService;
        this.secure = secure;
    }

    public ResponseCookie create(String token) {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(tokenService.accessTokenTtl())
                .build();
    }
}
