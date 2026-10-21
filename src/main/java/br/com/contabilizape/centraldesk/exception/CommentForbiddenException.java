package br.com.contabilizape.centraldesk.exception;

/** Ver o chamado não basta para comentar: a conversa é entre o solicitante e o responsável. */
public class CommentForbiddenException extends RuntimeException {

    public CommentForbiddenException() {
        super("Só o solicitante e o responsável pelo chamado podem comentar.");
    }
}
