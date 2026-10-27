package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.model.CodePurpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * HMAC-SHA256 dos códigos enviados por e-mail, com uma chave que fica só na variável de ambiente
 * (OTP_HMAC_KEY). Um código de 6 dígitos tem só 1 milhão de combinações: um hash simples seria
 * quebrado em milissegundos por quem vazasse o banco; com a chave fora do banco, não.
 *
 * A finalidade e o usuário entram na mensagem: o código de "esqueci minha senha" não vale como
 * código de acesso, nem o código de uma pessoa no fluxo de outra.
 */
@Component
public class OneTimeCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    public OneTimeCodeHasher(@Value("${app.security.otp-hmac-key:}") String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("OTP_HMAC_KEY precisa estar em base64 (ver README).", e);
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("OTP_HMAC_KEY ausente ou curta: use 32 bytes aleatórios em base64 (ver README).");
        }
        this.key = new SecretKeySpec(bytes, ALGORITHM);
    }

    /** Valor guardado no banco: 64 caracteres hexadecimais. */
    public String tag(CodePurpose purpose, UUID userId, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((purpose.name() + "|" + userId + "|" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível na JVM", e);
        }
    }

    /** Comparação em tempo constante: o tempo da resposta não diz quantos caracteres acertou. */
    public boolean matches(String storedTag, CodePurpose purpose, UUID userId, String code) {
        byte[] expected = storedTag.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = tag(purpose, userId, code).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }
}
