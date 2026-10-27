package br.com.contabilizape.centraldesk.email;

/** Um e-mail de conta já renderizado, à espera do commit para sair ({@link EmailReadyListener}). */
public record EmailReadyEvent(EmailMessage message) {
}
