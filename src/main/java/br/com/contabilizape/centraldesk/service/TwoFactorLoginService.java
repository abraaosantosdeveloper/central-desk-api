package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.AccountBlockedException;
import br.com.contabilizape.centraldesk.exception.InvalidLoginChallengeException;
import br.com.contabilizape.centraldesk.exception.InvalidVerificationCodeException;
import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.model.LoginChallenge;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.BrowserLabel;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import br.com.contabilizape.centraldesk.security.LoginAttemptLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Segunda etapa do login (PLANO-SEGURANCA §9.1). Código errado depois da senha certa prova que a senha
 * vazou: conta para o bloqueio da conta (B5) e o dono é avisado (uma vez por desafio).
 */
@Service
public class TwoFactorLoginService {

    private final LoginChallengeService challenges;
    private final VerificationCodeService codes;
    private final UserRepository userRepository;
    private final LoginLockout loginLockout;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final TrustedDeviceService trustedDevices;
    private final SecurityEventService securityEvents;
    private final AccountEmails accountEmails;
    private final AuthService authService;
    private final Clock clock;

    public TwoFactorLoginService(LoginChallengeService challenges, VerificationCodeService codes,
                                 UserRepository userRepository, LoginLockout loginLockout,
                                 LoginAttemptLimiter loginAttemptLimiter, TrustedDeviceService trustedDevices,
                                 SecurityEventService securityEvents, AccountEmails accountEmails,
                                 AuthService authService, Clock clock) {
        this.challenges = challenges;
        this.codes = codes;
        this.userRepository = userRepository;
        this.loginLockout = loginLockout;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.trustedDevices = trustedDevices;
        this.securityEvents = securityEvents;
        this.accountEmails = accountEmails;
        this.authService = authService;
        this.clock = clock;
    }

    /** A sessão aberta e, se a pessoa pediu, o token do navegador confiável (cookie de 30 dias). */
    public record Completed(AuthenticatedSession session, String trustedDeviceToken) {
    }

    /**
     * As recusas não desfazem a transação: a falha contada, o bloqueio e o aviso ao dono ficam gravados.
     */
    @Transactional(noRollbackFor = {InvalidVerificationCodeException.class, InvalidLoginChallengeException.class,
            AccountBlockedException.class})
    public Completed complete(String rawChallenge, String code, boolean trustThisBrowser, ClientInfo client) {
        loginAttemptLimiter.ensureAllowed(client.ip());
        LoginChallenge challenge = challenges.findUsableForUpdate(rawChallenge);
        // A linha da conta travada, como no login: tentativas simultâneas contam uma a uma
        User user = userRepository.findByEmailForUpdate(challenge.getUser().getEmail())
                .orElseThrow(InvalidLoginChallengeException::new);
        if (user.isBlocked()) {
            throw new AccountBlockedException();
        }
        if (!user.isActive()) {
            throw new InvalidLoginChallengeException();
        }

        if (!codes.verify(user.getId(), CodePurpose.LOGIN_TFA, code)) {
            challenge.registerFailure();
            loginAttemptLimiter.recordFailure(client.ip());
            loginLockout.registerFailure(user, client, SecurityEventType.TFA_FAILED);
            if (challenge.markFailureNotified()) {
                accountEmails.twoFactorFailedAfterPassword(user);
            }
            throw new InvalidVerificationCodeException();
        }

        challenge.consume(OffsetDateTime.now(clock));
        user.resetFailedLogins();
        String deviceToken = null;
        if (trustThisBrowser) {
            deviceToken = trustedDevices.trust(user, client.userAgent());
            securityEvents.record(SecurityEventType.NEW_TRUSTED_BROWSER, user, client, BrowserLabel.of(client.userAgent()));
        }
        accountEmails.newBrowserLogin(user, BrowserLabel.of(client.userAgent()));
        return new Completed(authService.startSession(user), deviceToken);
    }
}
