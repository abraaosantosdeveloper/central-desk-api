package br.com.contabilizape.centraldesk.exception;

/** Destino da reatribuição inválido: precisa ser conta ativa da Inovação ou admin, e não o solicitante. */
public class InvalidAssigneeException extends InvalidFieldException {

    public InvalidAssigneeException(String message) {
        super("assigneeId", message);
    }
}
