package br.com.contabilizape.centraldesk.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.util.Base64;

/**
 * Chave, emissor e validador do JWT. HS256: a mesma chave assina e confere,
 * o que basta enquanto só o próprio back-end emite e lê os tokens.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    // HS256 exige chave de pelo menos 256 bits
    private static final int MIN_KEY_BYTES = 32;

    @Bean
    public SecretKey jwtSigningKey(JwtProperties properties) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.secret().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET precisa estar em base64", e);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET precisa ter pelo menos 256 bits (32 bytes); tem " + key.length * 8 + " bits");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /**
     * Decoder usado nas requisições: além de assinatura, validade e emissor, recusa tokens emitidos
     * antes do corte de sessões do usuário (SCRUM-176).
     */
    @Bean
    public JwtDecoder accessTokenDecoder(SecretKey jwtSigningKey, JwtProperties properties,
                                         SessionCutoffValidator sessionCutoff) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) jwtDecoder(jwtSigningKey, properties);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), sessionCutoff));
        return decoder;
    }

    /** Só a parte criptográfica (assinatura, validade e emissor), sem consultar o banco. */
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Confere assinatura, validade (exp/nbf) e o emissor
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
