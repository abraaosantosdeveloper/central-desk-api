package br.com.contabilizape.centraldesk.bootstrap;

import br.com.contabilizape.centraldesk.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe a aplicação com as credenciais do admin configuradas (como viriam do .env)
 * e confere o que ficou gravado no PostgreSQL.
 */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.bootstrap.admin.name=Ana Administradora",
        "app.bootstrap.admin.cpf=11144477735",
        "app.bootstrap.admin.email=ana.admin@contabilizape.com.br",
        "app.bootstrap.admin.password=senhaForte123"
})
@Import(TestcontainersConfiguration.class)
class AdminBootstrapIntegrationTest {

    private static final String INNOVATION_ID = "e9790f1f-41df-4369-bfe7-1be4be535561";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Test
    void createsInitialAdminOnStartup() {
        Map<String, Object> admin = jdbc.queryForMap("""
                SELECT u.department_id, u.status, u.password_hash, a.granted_by_id
                FROM users u
                JOIN admins a ON a.user_id = u.id
                WHERE u.email = ?
                """, "ana.admin@contabilizape.com.br");

        assertThat(admin.get("department_id").toString()).isEqualTo(INNOVATION_ID);
        assertThat(admin.get("status")).isEqualTo("ACTIVE");
        assertThat((String) admin.get("password_hash")).startsWith("$2a$");
        assertThat(admin.get("granted_by_id")).isNull();
    }

    @Test
    void runningAgainDoesNotCreateAnotherAdmin() {
        adminBootstrap.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admins", Integer.class)).isEqualTo(1);
    }
}
