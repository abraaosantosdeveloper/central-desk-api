package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.CodeRequestLimitException;
import br.com.contabilizape.centraldesk.exception.InvalidLoginChallengeException;
import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.model.LoginChallenge;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.LoginChallengeRepository;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import br.com.contabilizape.centraldesk.security.OpaqueTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Desafio da verificação em duas etapas (PLANO-SEGURANCA §9.1): criado quando a senha confere, guardado
 * só como hash, e o código LOGIN_TFA vai por e-mail. Nenhum token de sessão existe nesta etapa.
 */
@Service
public class LoginChallengeService {

    public static final Duration VALIDITY = Duration.ofMinutes(5);

    private final LoginChallengeRepository repository;
    private final VerificationCodeService codes;
    private final Clock clock;

    public LoginChallengeService(LoginChallengeRepository repository, VerificationCodeService codes, Clock clock) {
        this.repository = repository;
        this.codes = codes;
        this.clock = clock;
    }

    /**
     * Cria o desafio e manda o código. Login repetido em menos de 60 s não manda outro código: o que já
     * foi enviado continua valendo. Acima do limite por hora, 429.
     *
     * @return o token em claro do desafio (vai só para o cookie tfa_challenge)
     */
    @Transactional(noRollbackFor = CodeRequestLimitException.class)
    public String start(User user, ClientInfo client) {
        String rawToken = OpaqueTokens.generate();
        repository.save(new LoginChallenge(user, OpaqueTokens.sha256(rawToken), now().plus(VALIDITY), client.ip()));
        sendCode(user, client, true);
        return rawToken;
    }

    /** Novo código para o mesmo desafio (botão "reenviar"); respeita os limites (429). */
    @Transactional
    public void resend(String rawToken, ClientInfo client) {
        LoginChallenge challenge = repository.findByTokenHash(hashOf(rawToken))
                .filter(found -> found.isUsableAt(now()))
                .orElseThrow(InvalidLoginChallengeException::new);
        sendCode(challenge.getUser(), client, false);
    }

    /**
     * O desafio vigente do cookie, travado (as tentativas contam uma a uma). A recusa não marca a
     * transação de quem chamou para desfazer.
     */
    @Transactional(noRollbackFor = InvalidLoginChallengeException.class)
    public LoginChallenge findUsableForUpdate(String rawToken) {
        return repository.findByTokenHashForUpdate(hashOf(rawToken))
                .filter(found -> found.isUsableAt(now()))
                .orElseThrow(InvalidLoginChallengeException::new);
    }

    @Transactional
    public int deleteExpired() {
        return repository.deleteExpiredBefore(now().minusDays(1));
    }

    private void sendCode(User user, ClientInfo client, boolean toleratePreviousCode) {
        try {
            codes.send(user, CodePurpose.LOGIN_TFA, client.ip());
        } catch (CodeRequestLimitException e) {
            // Intervalo de 60 s: o código enviado há pouco continua valendo. O limite por hora é recusado.
            if (!toleratePreviousCode || e.getRetryAfter().compareTo(Duration.ofSeconds(60)) > 0) {
                throw e;
            }
        }
    }

    private static String hashOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidLoginChallengeException();
        }
        return OpaqueTokens.sha256(rawToken);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
