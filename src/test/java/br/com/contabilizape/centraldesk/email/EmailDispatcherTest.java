package br.com.contabilizape.centraldesk.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-mails de conta: montados na hora (a conta ainda está carregada) e entregues só depois do commit.
 */
class EmailDispatcherTest {

    @Test
    void composesNowAndPublishesForDeliveryAfterCommit() {
        EmailRenderer renderer = mock(EmailRenderer.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        EmailMessage message = new EmailMessage("a@contabilizape.com.br", "Seu código de acesso", "<p/>", "k", Map.of(), true);
        when(renderer.compose(eq(EmailTemplate.LOGIN_CODE), eq("a@contabilizape.com.br"), any(), eq("k"), isNull()))
                .thenReturn(message);

        new EmailDispatcher(renderer, publisher).dispatch(EmailTemplate.LOGIN_CODE, "a@contabilizape.com.br",
                Map.of("recipientName", "Ana"), "k");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new EmailReadyEvent(message));
    }

    @Test
    void listenerHandsTheMessageToTheSender() {
        EmailSender sender = mock(EmailSender.class);
        EmailMessage message = new EmailMessage("a@contabilizape.com.br", "Assunto", "<p/>", "k", Map.of());

        new EmailReadyListener(sender).on(new EmailReadyEvent(message));

        verify(sender).send(message);
    }
}
