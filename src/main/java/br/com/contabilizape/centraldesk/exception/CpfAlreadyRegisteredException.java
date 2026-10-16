package br.com.contabilizape.centraldesk.exception;

public class CpfAlreadyRegisteredException extends RuntimeException {

    public CpfAlreadyRegisteredException() {
        super("CPF já cadastrado");
    }
}
