package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCookieTest {

    @Mock private RefreshTokenService refreshTokenService;

    private RefreshTokenCookie refreshTokenCookie;

    @BeforeEach
    void setUp() {
        refreshTokenCookie = new RefreshTokenCookie(refreshTokenService, true);
    }

    @Test
    void createdCookieOnlyTravelsToAuthRoutes() {
        when(refreshTokenService.ttl()).thenReturn(Duration.ofDays(7));

        ResponseCookie cookie = refreshTokenCookie.create("refresh-123");

        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("refresh-123");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
        assertProtected(cookie);
    }

    @Test
    void clearedCookieIsEmptyAndExpiresImmediately() {
        ResponseCookie cookie = refreshTokenCookie.clear();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertProtected(cookie);
    }

    private static void assertProtected(ResponseCookie cookie) {
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        // Só /auth/refresh e /auth/logout recebem o refresh token
        assertThat(cookie.getPath()).isEqualTo("/auth");
    }
}
