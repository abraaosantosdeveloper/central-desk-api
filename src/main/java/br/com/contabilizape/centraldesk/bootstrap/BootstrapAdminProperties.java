package br.com.contabilizape.centraldesk.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do primeiro administrador, lidas de app.bootstrap.admin.* (variáveis ADMIN_* no .env).
 * Ficam fora do repositório: cada ambiente define o seu.
 */
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record BootstrapAdminProperties(String name, String cpf, String email, String password) {

    public boolean isComplete() {
        return hasText(name) && hasText(cpf) && hasText(email) && hasText(password);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
