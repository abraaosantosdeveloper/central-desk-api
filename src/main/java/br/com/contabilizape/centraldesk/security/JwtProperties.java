package br.com.contabilizape.centraldesk.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuração do JWT (app.security.jwt.*). O segredo vem de JWT_SECRET, fora do Git.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank(message = "defina JWT_SECRET no .env (base64 de 32 bytes aleatórios)") String secret,
        @NotNull Duration accessTokenTtl,
        @NotBlank String issuer
) {
}
