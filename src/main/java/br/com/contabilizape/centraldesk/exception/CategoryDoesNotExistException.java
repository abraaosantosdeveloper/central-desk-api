package br.com.contabilizape.centraldesk.exception;

import java.util.UUID;

/** Categoria inexistente ou arquivada, buscada pelo próprio caminho (404). */
public class CategoryDoesNotExistException extends RuntimeException {

    public CategoryDoesNotExistException(UUID categoryId) {
        super("Categoria não encontrada: " + categoryId);
    }
}
