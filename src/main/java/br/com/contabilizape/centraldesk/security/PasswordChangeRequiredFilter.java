package br.com.contabilizape.centraldesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Primeiro acesso com a senha temporária dada por um administrador (PLANO-SEGURANCA §6.2): até a
 * pessoa escolher a senha dela, a API recusa tudo com 403 {@code password-change-required}, menos ver
 * o próprio perfil, trocar a senha e sair. Roda depois da autenticação pelo token.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    static final RequestMatcher ALLOWED = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.GET, "/users/me"),
            PATHS.matcher(HttpMethod.PATCH, "/users/me/password"),
            PATHS.matcher(HttpMethod.POST, "/auth/logout"),
            PATHS.matcher("/error")
    );

    private static final String BODY = """
            {"type":"about:blank","title":"Troca de senha obrigatória","status":403,\
            "detail":"Defina uma senha nova para continuar.","code":"password-change-required"}""";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean mustChange = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> Roles.PASSWORD_CHANGE_REQUIRED.equals(authority.getAuthority()));
        if (mustChange && !ALLOWED.matches(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/problem+json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(BODY);
            return;
        }
        chain.doFilter(request, response);
    }
}
