package br.com.contabilizape.centraldesk.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Emissão e validação do JWT com os beans reais do JwtConfig (sem Spring).
 */
class TokenServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString("chave-de-teste-com-32-bytes-ok!!".getBytes());
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString("outra-chave-de-teste-32-bytes!!!".getBytes());
    private static final UUID USER_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final String EMAIL = "maria.silva@contabilizape.com.br";

    private final JwtConfig config = new JwtConfig();

    @Test
    void issuesTokenWithUserClaimsAndFifteenMinutesOfValidity() {
        Instant now = Instant.now();
        TokenService tokenService = tokenService(SECRET, Clock.fixed(now, ZoneOffset.UTC));

        String token = tokenService.issueAccessToken(USER_ID, EMAIL, List.of(Roles.ADMIN, Roles.TICKET_HANDLER));
        Jwt jwt = decoder(SECRET).decode(token);

        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getClaimAsString(TokenService.EMAIL_CLAIM)).isEqualTo(EMAIL);
        assertThat(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM)).containsExactly("ROLE_ADMIN", "TICKET_HANDLER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("centraldesk");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(tokenService.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void userWithoutRolesGetsEmptyRolesClaim() {
        String token = tokenService(SECRET, Clock.systemUTC()).issueAccessToken(USER_ID, EMAIL, List.of());

        assertThat(decoder(SECRET).decode(token).getClaimAsStringList(TokenService.ROLES_CLAIM)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = tokenService(OTHER_SECRET, Clock.systemUTC()).issueAccessToken(USER_ID, EMAIL, List.of());

        assertThatThrownBy(() -> decoder(SECRET).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        Clock oneHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String token = tokenService(SECRET, oneHourAgo).issueAccessToken(USER_ID, EMAIL, List.of());

        assertThatThrownBy(() -> decoder(SECRET).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        JwtProperties otherIssuer = new JwtProperties(SECRET, Duration.ofMinutes(15), "outro-sistema");
        TokenService foreign = new TokenService(config.jwtEncoder(config.jwtSigningKey(otherIssuer)), otherIssuer, Clock.systemUTC());
        String token = foreign.issueAccessToken(USER_ID, EMAIL, List.of());

        assertThatThrownBy(() -> decoder(SECRET).decode(token)).isInstanceOf(JwtException.class);
    }

    private TokenService tokenService(String secret, Clock clock) {
        JwtProperties properties = properties(secret);
        return new TokenService(config.jwtEncoder(config.jwtSigningKey(properties)), properties, clock);
    }

    private JwtDecoder decoder(String secret) {
        JwtProperties properties = properties(secret);
        return config.jwtDecoder(config.jwtSigningKey(properties), properties);
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, Duration.ofMinutes(15), "centraldesk");
    }
}
