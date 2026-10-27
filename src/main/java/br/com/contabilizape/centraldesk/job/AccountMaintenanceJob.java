package br.com.contabilizape.centraldesk.job;

import br.com.contabilizape.centraldesk.service.VerificationCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Faxina diária das contas (madrugada, horário de Recife): códigos vencidos.
 * O @EnableScheduling já está em RealtimeConfig.
 */
@Component
public class AccountMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(AccountMaintenanceJob.class);

    private final VerificationCodeService verificationCodeService;

    public AccountMaintenanceJob(VerificationCodeService verificationCodeService) {
        this.verificationCodeService = verificationCodeService;
    }

    @Scheduled(cron = "0 17 3 * * *", zone = "America/Recife")
    public void run() {
        int codes = verificationCodeService.deleteExpired();
        log.info("Faxina das contas: {} códigos vencidos apagados", codes);
    }
}
