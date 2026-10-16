package br.com.contabilizape.centraldesk;

import org.junit.jupiter.api.Test;

@IntegrationTest
class CentralDeskApplicationTests {

	// Sobe a aplicação inteira contra um Postgres real com as migrations aplicadas.
	// Com ddl-auto=validate, falha se alguma entidade não bater com o schema.
	@Test
	void contextLoads() {
	}

}
