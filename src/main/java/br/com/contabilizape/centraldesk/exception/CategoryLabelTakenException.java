package br.com.contabilizape.centraldesk.exception;

public class CategoryLabelTakenException extends RuntimeException {

    public CategoryLabelTakenException(String label) {
        super("Já existe uma categoria chamada \"" + label + "\".");
    }
}
