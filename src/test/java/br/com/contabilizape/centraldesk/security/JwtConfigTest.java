package br.com.contabilizape.centraldesk.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @Test
    void acceptsKeyWith256Bits() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(config.jwtSigningKey(properties(secret)).getEncoded()).hasSize(32);
    }

    @Test
    void rejectsKeyShorterThan256Bits() {
        String secret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> config.jwtSigningKey(properties(secret)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void rejectsSecretThatIsNotBase64() {
        assertThatThrownBy(() -> config.jwtSigningKey(properties("isto não é base64!")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, Duration.ofMinutes(15), "centraldesk");
    }
}
