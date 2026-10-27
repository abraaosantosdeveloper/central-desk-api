package br.com.contabilizape.centraldesk.exception;

/** Senha certa num cadastro que ainda não confirmou o e-mail (403). */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Confirme seu e-mail para entrar. Enviamos um código para a sua caixa de entrada.");
    }
}
