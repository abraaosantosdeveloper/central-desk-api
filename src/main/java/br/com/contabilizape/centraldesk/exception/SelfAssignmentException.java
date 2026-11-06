package br.com.contabilizape.centraldesk.exception;

/**
 * Quem abriu o chamado não pode assumi-lo (exceto administrador ou coordenador): o atendimento é feito por outra pessoa
 * (e a avaliação não pode ser de alguém sobre si mesmo).
 */
public class SelfAssignmentException extends RuntimeException {

    public SelfAssignmentException() {
        super("Quem abriu o chamado não pode assumi-lo.");
    }
}
