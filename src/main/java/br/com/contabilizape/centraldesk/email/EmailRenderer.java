package br.com.contabilizape.centraldesk.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Transforma um {@link EmailTemplate} e suas variáveis em HTML (Thymeleaf, templates/email/).
 * Os templates usam só th:text (com escape): nomes, títulos e comentários são digitados por usuários.
 */
@Component
public class EmailRenderer {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ITemplateEngine engine;
    private final String appUrl;

    public EmailRenderer(ITemplateEngine engine, @Value("${app.email.front-base-url}") String appUrl) {
        this.engine = engine;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    /** Recusa variáveis faltando: é erro de programação, e um e-mail com buracos não deve sair. */
    public String render(EmailTemplate template, Map<String, Object> variables) {
        Set<String> missing = new TreeSet<>(template.variables());
        missing.removeAll(variables.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Variáveis faltando no e-mail " + template.file() + ": " + missing);
        }
        Context context = new Context(PT_BR);
        context.setVariables(variables);
        context.setVariable("appUrl", appUrl);
        return engine.process(template.view(), context);
    }

    public EmailMessage compose(EmailTemplate template, String to, Map<String, Object> variables,
                                String idempotencyKey, Long ticketNumber) {
        return new EmailMessage(to, template.subject(ticketNumber), render(template, variables), idempotencyKey,
                Map.of("type", template.tag()), template.isConfidential());
    }

    /** Endereço do front-end, para os links dos e-mails ({@code /chamados/<id>}, {@code /usuarios}). */
    public String appUrl() {
        return appUrl;
    }
}
