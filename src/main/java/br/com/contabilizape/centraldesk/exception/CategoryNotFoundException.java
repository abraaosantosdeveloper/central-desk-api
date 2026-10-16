package br.com.contabilizape.centraldesk.exception;

import java.util.UUID;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(UUID categoryId) {
        super("Categoria não encontrada: " + categoryId);
    }
}
