package br.com.contabilizape.centraldesk.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Lê o access token do cookie (e não do cabeçalho Authorization).
 *
 * Nas rotas públicas o cookie é ignorado: se o Spring tentasse validar um token vencido
 * guardado no navegador, responderia 401 até no login.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final RequestMatcher publicEndpoints;

    public CookieBearerTokenResolver(RequestMatcher publicEndpoints) {
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (publicEndpoints.matches(request) || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (AccessTokenCookie.NAME.equals(cookie.getName())
                    && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
