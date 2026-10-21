package br.com.contabilizape.centraldesk.model;

import br.com.contabilizape.centraldesk.security.ContentCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Ponto único da criptografia em repouso: o campo anotado com @Convert é cifrado ao gravar
 * e decifrado ao ler. O resto do código só vê o texto; o banco só vê a cifra.
 * O Hibernate cria o conversor pelo Spring, que injeta o ContentCipher.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final ContentCipher cipher;

    public EncryptedStringConverter(ContentCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        return cipher.encrypt(plainText);
    }

    @Override
    public String convertToEntityAttribute(String storedValue) {
        return cipher.decrypt(storedValue);
    }
}
