package br.com.contabilizape.centraldesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CentralDeskApplication {

	public static void main(String[] args) {
		System.out.println("DB_URL definida: "
				+ (System.getenv("DB_URL") != null));

		System.out.println("DB_URL começa com JDBC: "
				+ (System.getenv("DB_URL") != null
				&& System.getenv("DB_URL").startsWith("jdbc:")));

		SpringApplication.run(CentralDeskApplication.class, args);
	}

}
