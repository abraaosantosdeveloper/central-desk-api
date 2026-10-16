package br.com.contabilizape.centraldesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        // O application.properties importa o .env local; os testes não podem depender dele
        "app.bootstrap.admin.name=", "app.bootstrap.admin.cpf=",
        "app.bootstrap.admin.email=", "app.bootstrap.admin.password="
})
@Import(TestcontainersConfiguration.class)
class CentralDeskApplicationTests {

	// Sobe a aplicação inteira contra um Postgres real com as migrations aplicadas.
	// Com ddl-auto=validate, falha se alguma entidade não bater com o schema.
	@Test
	void contextLoads() {
	}

}
