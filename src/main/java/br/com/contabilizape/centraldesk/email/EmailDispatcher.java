package br.com.contabilizape.centraldesk.email;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Envio dos e-mails de conta (códigos, bloqueio, senha). O e-mail é montado na hora, dentro da
 * transação de quem chamou (a conta está carregada), e só sai depois do commit: uma operação que
 * falha e é desfeita não manda e-mail. Sem transação ativa, sai em seguida.
 */
@Component
public class EmailDispatcher {

    private final EmailRenderer renderer;
    private final ApplicationEventPublisher publisher;

    public EmailDispatcher(EmailRenderer renderer, ApplicationEventPublisher publisher) {
        this.renderer = renderer;
        this.publisher = publisher;
    }

    public void dispatch(EmailTemplate template, String to, Map<String, Object> variables, String idempotencyKey) {
        publisher.publishEvent(new EmailReadyEvent(renderer.compose(template, to, variables, idempotencyKey, null)));
    }
}
