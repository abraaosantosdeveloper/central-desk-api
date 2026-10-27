package br.com.contabilizape.centraldesk.email;

import java.util.Map;

/**
 * Um e-mail pronto para sair: um destinatário, assunto e corpo HTML já renderizados.
 *
 * @param idempotencyKey identifica este envio (ex.: {@code ticket-closed/<evento>/<destinatário>});
 *                       o Resend não repete um e-mail com a mesma chave dentro de 24 horas
 * @param tags           rótulos para filtrar no painel do Resend; nome e valor só aceitam
 *                       letras ASCII, números, "_" e "-"
 * @param confidential   leva código ou trata da segurança da conta: o corpo nunca vai para o log
 *                       e o destinatário aparece mascarado ({@link EmailTemplate#isConfidential()})
 */
public record EmailMessage(String to, String subject, String html, String idempotencyKey, Map<String, String> tags,
                           boolean confidential) {

    public EmailMessage {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }

    public EmailMessage(String to, String subject, String html, String idempotencyKey, Map<String, String> tags) {
        this(to, subject, html, idempotencyKey, tags, false);
    }

    /** "maria.souza@contabilizape.com.br" → "m***@contabilizape.com.br", para logs. */
    public String maskedRecipient() {
        int at = to == null ? -1 : to.indexOf('@');
        return at <= 0 ? "***" : to.charAt(0) + "***" + to.substring(at);
    }
}
