package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.email.EmailDispatcher;
import br.com.contabilizape.centraldesk.email.EmailFormats;
import br.com.contabilizape.centraldesk.email.EmailRenderer;
import br.com.contabilizape.centraldesk.email.EmailTemplate;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Bloqueio depois de 5 tentativas erradas seguidas (PLANO-SEGURANCA §5): senha errada ou, com a senha
 * certa, código de acesso errado. Roda na transação do login, com a linha do usuário travada
 * (findByEmailForUpdate): tentativas simultâneas contam uma a uma e bloqueiam uma vez só (B11).
 */
@Component
public class LoginLockout {

    public static final int MAX_FAILED_LOGINS = 5;

    private final RefreshTokenService refreshTokenService;
    private final SecurityEventService securityEvents;
    private final UserRepository userRepository;
    private final EmailDispatcher emails;
    private final EmailRenderer renderer;
    private final EmailFormats formats;
    private final Clock clock;

    public LoginLockout(RefreshTokenService refreshTokenService, SecurityEventService securityEvents,
                        UserRepository userRepository, EmailDispatcher emails, EmailRenderer renderer,
                        EmailFormats formats, Clock clock) {
        this.refreshTokenService = refreshTokenService;
        this.securityEvents = securityEvents;
        this.userRepository = userRepository;
        this.emails = emails;
        this.renderer = renderer;
        this.formats = formats;
        this.clock = clock;
    }

    /** Conta uma falha da conta ativa; na 5ª, bloqueia, derruba as sessões e avisa o dono e os admins. */
    public void registerFailure(User user, ClientInfo client, SecurityEventType type) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean blocked = user.registerFailedLogin(now, MAX_FAILED_LOGINS);
        securityEvents.record(type, user, client, "falha " + user.getFailedLoginAttempts() + " de " + MAX_FAILED_LOGINS);
        if (blocked) {
            block(user, client, now);
        }
    }

    private void block(User user, ClientInfo client, OffsetDateTime now) {
        // Sessões e navegadores confiáveis caem na hora (os navegadores, pelo SessionsEndedEvent)
        refreshTokenService.endAllSessions(user);
        securityEvents.record(SecurityEventType.ACCOUNT_BLOCKED, user, client, null);

        String date = formats.date(now);
        String time = formats.time(now);
        String key = "account-blocked/" + user.getId() + "/" + now.toEpochSecond();
        emails.dispatch(EmailTemplate.ACCOUNT_BLOCKED_OWNER, user.getEmail(), Map.of(
                "recipientName", user.getFullName(), "eventDate", date, "eventTime", time), key);
        for (User admin : userRepository.findAdminsByStatus(UserStatus.ACTIVE)) {
            if (admin.getId().equals(user.getId())) {
                continue;
            }
            emails.dispatch(EmailTemplate.ACCOUNT_BLOCKED_ADMINS, admin.getEmail(), Map.of(
                    "recipientName", admin.getFullName(),
                    "blockedUserName", user.getFullName(),
                    "blockedUserEmail", user.getEmail(),
                    "eventDate", date,
                    "eventTime", time,
                    "usersUrl", renderer.appUrl() + "/usuarios"
            ), key + "/" + admin.getId());
        }
    }
}
