package br.com.contabilizape.centraldesk.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * De onde veio a requisição: IP (já resolvido pelo Tomcat a partir do X-Forwarded-For do proxy
 * confiável, server.forward-headers-strategy=native) e User-Agent, para a auditoria e os limites.
 */
public record ClientInfo(String ip, String userAgent) {

    private static final int MAX_USER_AGENT = 255;

    public ClientInfo {
        userAgent = userAgent == null || userAgent.length() <= MAX_USER_AGENT ? userAgent : userAgent.substring(0, MAX_USER_AGENT);
    }

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
