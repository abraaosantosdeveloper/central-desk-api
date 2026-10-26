package br.com.contabilizape.centraldesk.email;

import java.util.Map;

/**
 * Um e-mail pronto para sair: um destinatário, assunto e corpo HTML já renderizados.
 *
 * @param idempotencyKey identifica este envio (ex.: {@code ticket-closed/<evento>/<destinatário>});
 *                       o Resend não repete um e-mail com a mesma chave dentro de 24 horas
 * @param tags           rótulos para filtrar no painel do Resend; nome e valor só aceitam
 *                       letras ASCII, números, "_" e "-"
 */
public record EmailMessage(String to, String subject, String html, String idempotencyKey, Map<String, String> tags) {

    public EmailMessage {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}
