package br.com.contabilizape.centraldesk;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Limpeza entre testes de integração (o contexto e o banco são compartilhados entre as classes).
 * A ordem respeita as chaves estrangeiras: primeiro o que aponta para users.
 */
public final class DatabaseCleanup {

    private DatabaseCleanup() {
    }

    /** Remove usuários comuns e tudo o que depende deles; administradores são preservados. */
    public static void removeNonAdminUsers(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM department_requests");
        jdbc.update("DELETE FROM users WHERE id NOT IN (SELECT user_id FROM admins)");
    }
}
