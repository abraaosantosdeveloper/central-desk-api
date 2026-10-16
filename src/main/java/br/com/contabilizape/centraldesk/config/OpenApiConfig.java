package br.com.contabilizape.centraldesk.config;

import br.com.contabilizape.centraldesk.security.AccessTokenCookie;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dados gerais da documentação OpenAPI. Os endpoints são descritos nas interfaces de
 * controller.doc, para os controllers ficarem só com o código.
 */
@Configuration
public class OpenApiConfig {

    /** Nome do esquema de segurança usado em @SecurityRequirement nos endpoints protegidos. */
    public static final String COOKIE_AUTH = "cookieAuth";

    @Bean
    public OpenAPI centralDeskOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CentralDesk API")
                .version("v1")
                .description("""
                        API da central de chamados internos da Contabiliza.

                        Erros seguem o formato ProblemDetail (RFC 9457), com Content-Type \
                        `application/problem+json`. Em erros de validação (400), o campo `errors` \
                        traz a mensagem de cada campo inválido.

                        Autenticação: faça o login em `POST /auth/login`. O token fica no cookie \
                        `access_token`, que o navegador envia sozinho nas requisições seguintes."""))
                .components(new Components().addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(AccessTokenCookie.NAME)
                        .description("JWT emitido pelo login, em cookie HttpOnly (o navegador envia sozinho)")));
    }
}
