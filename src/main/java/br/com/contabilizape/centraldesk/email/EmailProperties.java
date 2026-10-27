package br.com.contabilizape.centraldesk.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Configuração dos e-mails ({@code app.email.*}). Sem chave do Resend, os e-mails vão só para o log.
 *
 * @param from            remetente, no formato {@code Nome <endereco@dominio>}; o domínio precisa estar verificado no Resend
 * @param async           {@code true} envia em threads próprias (produção); {@code false} envia na mesma thread (testes)
 * @param required        {@code true}: a aplicação não sobe sem a chave do Resend (no Railway, sempre)
 * @param frontBaseUrl    endereço do front-end, usado nos links dos e-mails
 * @param zone            fuso das datas mostradas nos e-mails
 * @param maxMessageChars trecho máximo de um comentário no e-mail
 */
@ConfigurationProperties("app.email")
public record EmailProperties(String from, boolean async, boolean required, String frontBaseUrl, ZoneId zone,
                              Integer maxMessageChars, Resend resend) {

    public EmailProperties {
        frontBaseUrl = frontBaseUrl == null || frontBaseUrl.isBlank() ? "http://localhost:5173" : frontBaseUrl;
        zone = zone == null ? ZoneId.of("America/Recife") : zone;
        maxMessageChars = maxMessageChars == null ? 1500 : maxMessageChars;
    }

    public record Resend(String apiKey, String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }

    public boolean hasResendKey() {
        return resend != null && resend.apiKey() != null && !resend.apiKey().isBlank();
    }
}
