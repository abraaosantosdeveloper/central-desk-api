package br.com.contabilizape.centraldesk.config;

import br.com.contabilizape.centraldesk.security.CookieBearerTokenResolver;
import br.com.contabilizape.centraldesk.security.Roles;
import br.com.contabilizape.centraldesk.security.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            // Chamado justamente quando o access token venceu; o refresh token vem no próprio cookie
            PATHS.matcher(HttpMethod.POST, "/auth/refresh"),
            // Público de propósito: com a sessão vencida, o logout ainda precisa apagar o cookie
            PATHS.matcher(HttpMethod.POST, "/auth/logout"),
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
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(PATHS.matcher(HttpMethod.PATCH, "/users/*/anonymize")).hasAuthority(Roles.ADMIN)
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(new CookieBearerTokenResolver(PUBLIC_ENDPOINTS))
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
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
