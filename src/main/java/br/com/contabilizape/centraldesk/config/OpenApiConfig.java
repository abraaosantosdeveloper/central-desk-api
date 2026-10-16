package br.com.contabilizape.centraldesk.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dados gerais da documentação OpenAPI. Os endpoints são descritos nas interfaces de
 * controller.doc, para os controllers ficarem só com o código.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI centralDeskOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CentralDesk API")
                .version("v1")
                .description("""
                        API da central de chamados internos da Contabiliza.

                        Erros seguem o formato ProblemDetail (RFC 9457), com Content-Type \
                        `application/problem+json`. Em erros de validação (400), o campo `errors` \
                        traz a mensagem de cada campo inválido."""));
    }
}
