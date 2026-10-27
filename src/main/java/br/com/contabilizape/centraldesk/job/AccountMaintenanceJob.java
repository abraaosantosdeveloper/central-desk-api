package br.com.contabilizape.centraldesk.job;

import br.com.contabilizape.centraldesk.service.EmailVerificationService;
import br.com.contabilizape.centraldesk.service.LoginChallengeService;
import br.com.contabilizape.centraldesk.service.VerificationCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Faxina diária das contas (madrugada, horário de Recife): códigos e desafios de login vencidos, e cadastros que não
 * confirmaram o e-mail em 24 horas (o e-mail e o CPF voltam a ficar livres).
 * O @EnableScheduling já está em RealtimeConfig.
 */
@Component
public class AccountMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(AccountMaintenanceJob.class);

    private final VerificationCodeService verificationCodeService;
    private final EmailVerificationService emailVerificationService;
    private final LoginChallengeService loginChallengeService;

    public AccountMaintenanceJob(VerificationCodeService verificationCodeService,
                                 EmailVerificationService emailVerificationService,
                                 LoginChallengeService loginChallengeService) {
        this.verificationCodeService = verificationCodeService;
        this.emailVerificationService = emailVerificationService;
        this.loginChallengeService = loginChallengeService;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "America/Recife")
    public void run() {
        int registrations = emailVerificationService.purgeExpired();
        int codes = verificationCodeService.deleteExpired();
        loginChallengeService.deleteExpired();
        log.info("Faxina das contas: {} cadastros não confirmados e {} códigos vencidos apagados", registrations, codes);
    }
}
