package br.com.contabilizape.centraldesk.exception;

/** O sistema não pode ficar sem nenhum administrador ativo. */
public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("Esta é a última conta de administrador ativa e não pode ser removida.");
    }
}
