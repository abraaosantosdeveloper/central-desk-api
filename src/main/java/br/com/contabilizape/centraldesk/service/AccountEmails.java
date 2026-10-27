package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.email.EmailDispatcher;
import br.com.contabilizape.centraldesk.email.EmailFormats;
import br.com.contabilizape.centraldesk.email.EmailTemplate;
import br.com.contabilizape.centraldesk.model.User;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Avisos de segurança ao dono da conta (PLANO-SEGURANCA §10). Saem depois do commit, na fila prioritária.
 * Nenhum deles leva senha ou link de troca de senha.
 */
@Component
public class AccountEmails {

    private final EmailDispatcher dispatcher;
    private final EmailFormats formats;
    private final Clock clock;

    public AccountEmails(EmailDispatcher dispatcher, EmailFormats formats, Clock clock) {
        this.dispatcher = dispatcher;
        this.formats = formats;
        this.clock = clock;
    }

    public void passwordChanged(User user) {
        send(EmailTemplate.PASSWORD_CHANGED, user, Map.of());
    }

    public void passwordResetByAdmin(User user, User admin) {
        send(EmailTemplate.PASSWORD_RESET_BY_ADMIN, user, Map.of("adminName", admin.getFullName()));
    }

    public void blockedAccountResetAttempt(User user) {
        send(EmailTemplate.ACCOUNT_BLOCKED_RESET_ATTEMPT, user, Map.of());
    }

    public void twoFactorFailedAfterPassword(User user) {
        send(EmailTemplate.TFA_FAILED_AFTER_PASSWORD, user, Map.of());
    }

    public void newBrowserLogin(User user, String browserLabel) {
        send(EmailTemplate.NEW_BROWSER_LOGIN, user, Map.of("browserLabel", browserLabel));
    }

    public void twoFactorDisabled(User user) {
        send(EmailTemplate.TWO_FACTOR_DISABLED, user, Map.of());
    }

    private void send(EmailTemplate template, User user, Map<String, Object> extra) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, Object> variables = new HashMap<>(extra);
        variables.put("recipientName", user.getFullName());
        if (template.variables().contains("eventDate")) {
            variables.put("eventDate", formats.date(now));
            variables.put("eventTime", formats.time(now));
        }
        dispatcher.dispatch(template, user.getEmail(), variables,
                template.tag() + "/" + user.getId() + "/" + now.toInstant().toEpochMilli());
    }
}
