package br.com.contabilizape.centraldesk.config;

import br.com.contabilizape.centraldesk.security.CookieBearerTokenResolver;
import br.com.contabilizape.centraldesk.security.Roles;
import br.com.contabilizape.centraldesk.security.TokenService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import br.com.contabilizape.centraldesk.security.PasswordChangeRequiredFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /**
     * Rotas que não exigem login. A mesma lista libera o acesso e faz o cookie ser ignorado
     * (ver CookieBearerTokenResolver).
     */
    static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.POST, "/auth/register"),
            PATHS.matcher(HttpMethod.POST, "/auth/login"),
            // Segunda etapa do login: ainda não há sessão, só o cookie do desafio
            PATHS.matcher(HttpMethod.POST, "/auth/login/tfa"),
            PATHS.matcher(HttpMethod.POST, "/auth/login/tfa/resend"),
            // Chamado justamente quando o access token venceu; o refresh token vem no próprio cookie
            PATHS.matcher(HttpMethod.POST, "/auth/refresh"),
            // Público de propósito: com a sessão vencida, o logout ainda precisa apagar o cookie
            PATHS.matcher(HttpMethod.POST, "/auth/logout"),
            // "Esqueci minha senha": quem não lembra a senha não tem sessão
            PATHS.matcher(HttpMethod.POST, "/auth/password/forgot"),
            PATHS.matcher(HttpMethod.POST, "/auth/password/reset"),
            // Confirmação do e-mail do cadastro (a conta ainda não entra)
            PATHS.matcher(HttpMethod.POST, "/auth/email/verify"),
            PATHS.matcher(HttpMethod.POST, "/auth/email/resend"),
            PATHS.matcher(HttpMethod.GET, "/departments"),
            // O Spring encaminha os erros para /error; protegida, ela esconderia o erro real atrás de um 401
            PATHS.matcher("/error"),
            // Documentação da API: só responde quando API_DOCS_ENABLED=true; senão, 404
            PATHS.matcher("/v3/api-docs/**"),
            PATHS.matcher("/swagger-ui/**"),
            PATHS.matcher("/swagger-ui.html")
    );

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Sem sessão no servidor: cada requisição se autentica pelo JWT do cookie.
            // CSRF: o SameSite=Strict do cookie já barra requisições vindas de outros sites.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Fim de uma resposta assíncrona (stream SSE): a requisição original já foi autorizada
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                // O próprio perfil vale para qualquer usuário logado; precisa vir antes da regra de /users/**
                .requestMatchers(PATHS.matcher("/users/me"), PATHS.matcher("/users/me/**")).authenticated()
                // Painel do administrador: contas (inclui anonimizar e redefinir senha) e pedidos de departamento
                .requestMatchers(PATHS.matcher("/users"), PATHS.matcher("/users/**"),
                        PATHS.matcher("/department-requests"), PATHS.matcher("/department-requests/**"))
                    .hasAuthority(Roles.ADMIN)
                // Gestão de categorias; a lista (GET /categories) continua aberta a qualquer usuário logado
                .requestMatchers(PATHS.matcher(HttpMethod.POST, "/categories"), PATHS.matcher(HttpMethod.PATCH, "/categories/*"),
                        PATHS.matcher(HttpMethod.DELETE, "/categories/*"), PATHS.matcher(HttpMethod.GET, "/categories/usage"))
                    .hasAuthority(Roles.ADMIN)
                // Correção do setor de origem do chamado: só administradores (o serviço confere de novo no banco)
                .requestMatchers(PATHS.matcher(HttpMethod.PATCH, "/tickets/*/origin-department"))
                    .hasAuthority(Roles.ADMIN)
                .requestMatchers(PATHS.matcher(HttpMethod.GET, "/tickets/queue"), PATHS.matcher(HttpMethod.GET, "/tickets/queue/count"),
                        PATHS.matcher(HttpMethod.GET, "/tickets/assigned"))
                    .hasAnyAuthority(Roles.TICKET_HANDLER, Roles.ADMIN)
                .requestMatchers(PATHS.matcher(HttpMethod.PATCH, "/tickets/*/assign"), PATHS.matcher(HttpMethod.PATCH, "/tickets/*/reassign"),
                        PATHS.matcher(HttpMethod.GET, "/tickets/assignees"))
                    .hasAnyAuthority(Roles.TICKET_HANDLER, Roles.ADMIN)
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(new CookieBearerTokenResolver(PUBLIC_ENDPOINTS))
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            // Senha temporária: só perfil, troca de senha e logout até a pessoa escolher a dela
            .addFilterAfter(new PasswordChangeRequiredFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // As permissões vêm da claim "roles" do token, já com o nome final (ROLE_ADMIN, TICKET_HANDLER)
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
