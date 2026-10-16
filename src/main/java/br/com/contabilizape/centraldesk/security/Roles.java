package br.com.contabilizape.centraldesk.security;

/**
 * Authorities gravadas no token no login.
 */
public final class Roles {

    /** Usuário presente na tabela admins. */
    public static final String ADMIN = "ROLE_ADMIN";

    /** Membro do departamento que atende os chamados (Inovação). */
    public static final String TICKET_HANDLER = "TICKET_HANDLER";

    private Roles() {
    }
}
