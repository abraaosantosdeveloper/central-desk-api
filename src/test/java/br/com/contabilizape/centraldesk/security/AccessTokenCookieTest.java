package br.com.contabilizape.centraldesk.security;

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
class AccessTokenCookieTest {

    @Mock private TokenService tokenService;

    private AccessTokenCookie accessTokenCookie;

    @BeforeEach
    void setUp() {
        accessTokenCookie = new AccessTokenCookie(tokenService, true);
    }

    @Test
    void createdCookieLastsAsLongAsTheToken() {
        when(tokenService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        ResponseCookie cookie = accessTokenCookie.create("jwt-123");

        assertThat(cookie.getName()).isEqualTo("access_token");
        assertThat(cookie.getValue()).isEqualTo("jwt-123");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(15));
        assertProtected(cookie);
    }

    @Test
    void clearedCookieIsEmptyAndExpiresImmediately() {
        ResponseCookie cookie = accessTokenCookie.clear();

        assertThat(cookie.getName()).isEqualTo("access_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        // O navegador só apaga o cookie se nome, Path e atributos baterem com os do original
        assertProtected(cookie);
    }

    private static void assertProtected(ResponseCookie cookie) {
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
    }
}
