package br.com.contabilizape.centraldesk.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CookieBearerTokenResolverTest {

    // Nos testes, "público" é tudo sob /auth/
    private final CookieBearerTokenResolver resolver =
            new CookieBearerTokenResolver(request -> request.getRequestURI().startsWith("/auth/"));

    @Test
    void readsTokenFromAccessTokenCookie() {
        MockHttpServletRequest request = request("/users/me");
        request.setCookies(new Cookie("outro", "x"), new Cookie(AccessTokenCookie.NAME, "token-123"));

        assertThat(resolver.resolve(request)).isEqualTo("token-123");
    }

    @Test
    void returnsNullWithoutCookie() {
        assertThat(resolver.resolve(request("/users/me"))).isNull();
    }

    @Test
    void returnsNullForBlankCookie() {
        MockHttpServletRequest request = request("/users/me");
        request.setCookies(new Cookie(AccessTokenCookie.NAME, ""));

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void ignoresCookieOnPublicEndpoints() {
        // Um token vencido no navegador não pode impedir o login
        MockHttpServletRequest request = request("/auth/login");
        request.setCookies(new Cookie(AccessTokenCookie.NAME, "token-vencido"));

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void ignoresAuthorizationHeader() {
        // A API só aceita o token pelo cookie HttpOnly
        MockHttpServletRequest request = request("/users/me");
        request.addHeader("Authorization", "Bearer token-123");

        assertThat(resolver.resolve(request)).isNull();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
