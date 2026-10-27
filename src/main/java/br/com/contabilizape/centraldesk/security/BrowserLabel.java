package br.com.contabilizape.centraldesk.security;

import java.util.Locale;

/**
 * Resumo do User-Agent para a pessoa reconhecer o navegador na lista ("Chrome no Windows").
 * Não serve para identificar o navegador: quem identifica é o token do cookie.
 */
public final class BrowserLabel {

    private BrowserLabel() {
    }

    public static String of(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Navegador desconhecido";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return browser(ua) + " no " + system(ua);
    }

    private static String browser(String ua) {
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("chrome/") || ua.contains("crios/")) return "Chrome";
        if (ua.contains("safari/")) return "Safari";
        return "Navegador";
    }

    private static String system(String ua) {
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("mac os")) return "macOS";
        if (ua.contains("linux")) return "Linux";
        return "sistema desconhecido";
    }
}
