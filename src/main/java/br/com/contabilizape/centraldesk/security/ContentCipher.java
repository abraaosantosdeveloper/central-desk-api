package br.com.contabilizape.centraldesk.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifra e decifra o conteúdo dos comentários com AES-256-GCM.
 *
 * Formato gravado: {@code v1:} + base64(IV de 12 bytes + cifra + tag de 16 bytes).
 * O IV é aleatório a cada mensagem (duas mensagens iguais geram cifras diferentes) e a tag
 * do GCM faz a leitura falhar se a cifra tiver sido alterada. O prefixo de versão permite
 * trocar a chave no futuro sem perder o que já foi gravado.
 */
public final class ContentCipher {

    private static final String VERSION = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    private ContentCipher(byte[] keyBytes) {
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Valida a chave na subida da aplicação: sem uma chave AES-256 válida, nada é gravado. */
    public static ContentCipher fromBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "Defina CONTENT_ENCRYPTION_KEY no .env (base64 de 32 bytes aleatórios; comando no README)");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("CONTENT_ENCRYPTION_KEY precisa estar em base64", e);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "CONTENT_ENCRYPTION_KEY precisa ter 256 bits (32 bytes); tem " + keyBytes.length * 8 + " bits");
        }
        return new ContentCipher(keyBytes);
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] stored = ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
            return VERSION + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível cifrar o conteúdo", e);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        if (!storedValue.startsWith(VERSION)) {
            throw new IllegalStateException("Conteúdo cifrado com versão desconhecida (esperado " + VERSION + ")");
        }
        try {
            byte[] stored = Base64.getDecoder().decode(storedValue.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOf(stored, IV_BYTES)));
            byte[] plain = cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Chave errada ou cifra adulterada: a tag do GCM não confere
            throw new IllegalStateException("Não foi possível decifrar o conteúdo", e);
        }
    }
}
