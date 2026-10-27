package br.com.contabilizape.centraldesk.security;

/**
 * Authorities gravadas no token no login.
 */
public final class Roles {

    /** Usuário presente na tabela admins. */
    public static final String ADMIN = "ROLE_ADMIN";

    /** Membro do departamento que atende os chamados (Inovação). */
    public static final String TICKET_HANDLER = "TICKET_HANDLER";

    /**
     * Senha temporária dada por um administrador: enquanto a pessoa não escolhe a dela, a API só aceita
     * ver o perfil, trocar a senha e sair (PasswordChangeRequiredFilter).
     */
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private Roles() {
    }
}
