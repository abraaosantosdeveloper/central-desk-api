package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie do refresh token. Mesma proteção do access token, mas com Path=/auth:
 * o navegador só o envia para /auth/refresh e /auth/logout (e não em toda requisição da API).
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "refresh_token";
    private static final String PATH = "/auth";

    private final RefreshTokenService refreshTokenService;
    private final boolean secure;

    public RefreshTokenCookie(RefreshTokenService refreshTokenService,
                              @Value("${app.security.cookie.secure}") boolean secure) {
        this.refreshTokenService = refreshTokenService;
        this.secure = secure;
    }

    public ResponseCookie create(String rawToken) {
        return withProtection(ResponseCookie.from(NAME, rawToken))
                .maxAge(refreshTokenService.ttl())
                .build();
    }

    /** Cookie vazio que expira na hora, com o mesmo Path do original (senão o navegador não o apaga). */
    public ResponseCookie clear() {
        return withProtection(ResponseCookie.from(NAME, ""))
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder withProtection(ResponseCookie.ResponseCookieBuilder builder) {
        return builder
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
