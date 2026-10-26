package br.com.contabilizape.centraldesk.email;

/**
 * Entrega um e-mail. Duas implementações: {@link ResendEmailSender} (produção) e
 * {@link LoggingEmailSender} (desenvolvimento e CI, sem chave do Resend).
 */
public interface EmailSender {

    /**
     * Envia o e-mail. Nunca lança: a operação que o originou (abrir, assumir, encerrar...) já foi
     * gravada, e uma falha de envio só pode ser registrada em log.
     */
    void send(EmailMessage message);
}
