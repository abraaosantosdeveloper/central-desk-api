package br.com.contabilizape.centraldesk.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A chave vem da variável de ambiente CONTENT_ENCRYPTION_KEY e nunca é gravada no banco.
 * Sem uma chave válida a aplicação não sobe, para nunca gravar comentário em texto aberto.
 */
@Configuration
public class ContentEncryptionConfig {

    @Bean
    public ContentCipher contentCipher(@Value("${app.security.content-encryption-key:}") String base64Key) {
        return ContentCipher.fromBase64Key(base64Key);
    }
}
