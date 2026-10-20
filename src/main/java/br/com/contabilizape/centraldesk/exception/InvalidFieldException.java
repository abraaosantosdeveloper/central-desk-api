package br.com.contabilizape.centraldesk.exception;

/**
 * Erro de negócio ligado a um campo do formulário. Vira 400 com o campo em "errors",
 * no mesmo formato da validação do Bean Validation.
 */
public abstract class InvalidFieldException extends RuntimeException {

    private final String field;

    protected InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
