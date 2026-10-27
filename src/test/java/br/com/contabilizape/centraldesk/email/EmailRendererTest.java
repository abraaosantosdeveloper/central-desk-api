package br.com.contabilizape.centraldesk.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Renderização com o motor Thymeleaf de verdade: todo template sai com as variáveis do contrato,
 * texto de usuário sai escapado, e nada de dado sensível vai para o assunto.
 */
class EmailRendererTest {

    private static final String XSS = "<script>alert(1)</script><img src=x onerror=alert(2)>";

    private final EmailRenderer renderer = new EmailRenderer(engine(), "https://centraldesk.contabilizape.com.br");

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    void everyTemplateRendersWithItsVariables(EmailTemplate template) {
        String html = renderer.render(template, sample(template));

        assertThat(html).startsWith("<!DOCTYPE html>").contains("Maria Souza").contains("CentralDesk");
        assertThat(html).doesNotContainPattern("\\sth:[a-z]").doesNotContain("xmlns:th").doesNotContain("${");
        assertThat(html.length()).isLessThan(100_000); // o Gmail corta acima de ~102 KB
    }

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    void userTextIsAlwaysEscaped(EmailTemplate template) {
        Map<String, Object> variables = sample(template);
        variables.replaceAll((name, value) -> value instanceof String ? XSS : value);
        variables.computeIfPresent("messageLines", (name, value) -> List.of(XSS));
        variables.computeIfPresent("resolutionLines", (name, value) -> List.of(XSS));
        variables.computeIfPresent("reasonLines", (name, value) -> List.of(XSS));

        String html = renderer.render(template, variables);

        assertThat(html).doesNotContain("<script>").doesNotContain("<img src=x");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void missingVariableIsAProgrammingErrorNotABlankEmail() {
        Map<String, Object> variables = sample(EmailTemplate.TICKET_CLOSED);
        variables.remove("closedByName");

        assertThatThrownBy(() -> renderer.render(EmailTemplate.TICKET_CLOSED, variables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closedByName");
    }

    @Test
    void codeIsInTheBodyButNeverInTheSubject() {
        Map<String, Object> variables = sample(EmailTemplate.LOGIN_CODE);
        variables.put("code", "042917");

        EmailMessage message = renderer.compose(EmailTemplate.LOGIN_CODE, "maria.souza@contabilizape.com.br",
                variables, "login-code/1/2", null);

        assertThat(message.html()).contains("042917").contains("5 minutos");
        assertThat(message.subject()).isEqualTo("Seu código de acesso").doesNotContain("042917");
        assertThat(message.confidential()).isTrue();
        assertThat(message.tags()).containsEntry("type", "login_code");
    }

    @Test
    void codeEmailsTellWhatToDoIfItWasNotYou() {
        String html = renderer.render(EmailTemplate.PASSWORD_RESET_CODE, sample(EmailTemplate.PASSWORD_RESET_CODE));

        assertThat(html).contains("Se não foi você").contains("Inovação");
        // Sem link para trocar a senha: o código é digitado na tela do sistema (evita phishing que imite o link)
        assertThat(html).doesNotContain("/redefinir").doesNotContain("reset");
    }

    @Test
    void lineBreaksOfTheMessageBecomeBrTags() {
        Map<String, Object> variables = sample(EmailTemplate.TICKET_NEW_MESSAGE);
        variables.put("messageLines", List.of("Bom dia,", "a impressora voltou."));

        String html = renderer.render(EmailTemplate.TICKET_NEW_MESSAGE, variables);

        assertThat(html).containsPattern("Bom dia,</span>\\s*<br\\s*/?>\\s*<span[^>]*>a impressora voltou.");
    }

    @Test
    void truncatedMessageInvitesToKeepReadingInTheSystem() {
        Map<String, Object> variables = sample(EmailTemplate.TICKET_NEW_MESSAGE);
        variables.put("messageTruncated", true);

        assertThat(renderer.render(EmailTemplate.TICKET_NEW_MESSAGE, variables)).contains("Continue lendo no CentralDesk");
    }

    @Test
    void closedTicketSaysWhoClosedItAndWhen() {
        String html = renderer.render(EmailTemplate.TICKET_CLOSED, sample(EmailTemplate.TICKET_CLOSED));

        assertThat(html).containsPattern("#\\s*123").contains("Carlos Lima").contains("14:30").contains("23/10/2026");
    }

    @Test
    void reopenedTicketShowsWhereItWent() {
        Map<String, Object> variables = sample(EmailTemplate.TICKET_REOPENED);
        variables.put("backToQueue", true);
        variables.put("assigneeName", null);

        assertThat(renderer.render(EmailTemplate.TICKET_REOPENED, variables)).contains("voltou para a fila");
    }

    @Test
    void ticketEmailsLinkToTheTicket() {
        String html = renderer.render(EmailTemplate.TICKET_IN_PROGRESS, sample(EmailTemplate.TICKET_IN_PROGRESS));

        assertThat(html).contains("href=\"https://centraldesk.contabilizape.com.br/chamados/abc\"");
    }

    @Test
    void newMessageSubjectHasTheTicketNumber() {
        EmailMessage message = renderer.compose(EmailTemplate.TICKET_NEW_MESSAGE, "a@contabilizape.com.br",
                sample(EmailTemplate.TICKET_NEW_MESSAGE), "k", 123L);

        assertThat(message.subject()).isEqualTo("Nova mensagem no chamado #123");
        assertThat(message.confidential()).isFalse();
    }

    // ---------- auxiliares ----------

    static Map<String, Object> sample(EmailTemplate template) {
        Map<String, Object> variables = new HashMap<>();
        for (String name : template.variables()) {
            variables.put(name, switch (name) {
                case "recipientName" -> "Maria Souza";
                case "ticketNumber" -> 123L;
                case "ticketUrl" -> "https://centraldesk.contabilizape.com.br/chamados/abc";
                case "usersUrl" -> "https://centraldesk.contabilizape.com.br/usuarios";
                case "messageLines", "resolutionLines", "reasonLines" -> List.of("Troquei a fonte.", "Testado.");
                case "messageTruncated", "backToQueue" -> false;
                case "validityMinutes" -> template == EmailTemplate.LOGIN_CODE ? 5 : 10;
                case "code" -> "123456";
                case "closedByName", "assigneeName", "newAssigneeName", "authorName" -> "Carlos Lima";
                case "authorRole" -> "responsável";
                case "openedDate", "closedDate", "assignedDate", "sentDate", "reassignedDate", "reopenedDate",
                     "eventDate" -> "23/10/2026";
                case "openedTime", "closedTime", "assignedTime", "sentTime", "reassignedTime", "reopenedTime",
                     "eventTime" -> "14:30";
                default -> "Texto de " + name;
            });
        }
        return variables;
    }

    private static TemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        // Mesmo motor do Spring Boot (expressões em SpEL)
        TemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
