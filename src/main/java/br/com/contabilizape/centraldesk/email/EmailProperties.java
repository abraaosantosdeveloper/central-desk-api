package br.com.contabilizape.centraldesk.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração dos e-mails ({@code app.email.*}). Sem chave do Resend, os e-mails vão só para o log.
 *
 * @param from  remetente, no formato {@code Nome <endereco@dominio>}; o domínio precisa estar verificado no Resend
 * @param async {@code true} envia em threads próprias (produção); {@code false} envia na mesma thread (testes)
 */
@ConfigurationProperties("app.email")
public record EmailProperties(String from, boolean async, Resend resend) {

    public record Resend(String apiKey, String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }

    public boolean hasResendKey() {
        return resend != null && resend.apiKey() != null && !resend.apiKey().isBlank();
    }
}
