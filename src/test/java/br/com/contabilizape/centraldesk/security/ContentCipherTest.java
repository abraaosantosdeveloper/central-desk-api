package br.com.contabilizape.centraldesk.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AES-256-GCM do conteúdo dos comentários: o banco só guarda a cifra.
 */
class ContentCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("chave-de-teste-com-32-bytes-ok!!".getBytes());
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("outra-chave-de-32-bytes-tambem!!".getBytes());

    private final ContentCipher cipher = ContentCipher.fromBase64Key(KEY);

    @Test
    void encryptedTextGoesBackToTheOriginal() {
        String encrypted = cipher.encrypt("A impressora voltou a funcionar? Ação concluída ✅");

        assertThat(cipher.decrypt(encrypted)).isEqualTo("A impressora voltou a funcionar? Ação concluída ✅");
    }

    @Test
    void storedValueIsVersionedAndDoesNotContainThePlainText() {
        String encrypted = cipher.encrypt("senha do wifi é 1234");

        assertThat(encrypted).startsWith("v1:").doesNotContain("senha").doesNotContain("1234");
    }

    @Test
    void sameTextEncryptsDifferentlyEachTime() {
        // IV aleatório por mensagem: quem lê o banco não descobre que duas mensagens são iguais
        assertThat(cipher.encrypt("ok")).isNotEqualTo(cipher.encrypt("ok"));
    }

    @Test
    void tamperedCipherTextIsRejected() {
        String encrypted = cipher.encrypt("conteúdo original");
        byte[] bytes = Base64.getDecoder().decode(encrypted.substring(3));
        bytes[bytes.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        String encrypted = cipher.encrypt("conteúdo");

        assertThatThrownBy(() -> ContentCipher.fromBase64Key(OTHER_KEY).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownVersionIsRejected() {
        assertThatThrownBy(() -> cipher.decrypt("texto que nunca foi cifrado"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("versão");
    }

    @Test
    void nullStaysNull() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @ParameterizedTest(name = "recusa a chave \"{0}\"")
    @ValueSource(strings = {"", "   ", "isto não é base64!", "c2hvcnQ="})
    void invalidKeyFailsAtStartup(String key) {
        assertThatThrownBy(() -> ContentCipher.fromBase64Key(key))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONTENT_ENCRYPTION_KEY");
    }
}
