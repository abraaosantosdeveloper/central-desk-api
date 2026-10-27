package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.email.EmailDispatcher;
import br.com.contabilizape.centraldesk.exception.CodeRequestLimitException;
import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.VerificationCode;
import br.com.contabilizape.centraldesk.repository.VerificationCodeRepository;
import br.com.contabilizape.centraldesk.security.AccountSecurityPolicy;
import br.com.contabilizape.centraldesk.security.OneTimeCodeHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Códigos de 6 dígitos enviados por e-mail (PLANO-SEGURANCA §1 e §4): gera com SecureRandom, guarda
 * só o HMAC, confere em tempo constante e aplica a política única (validade, 5 tentativas, uso único,
 * 60 s entre pedidos, 5 pedidos por hora por conta e finalidade, limite por IP).
 */
@Service
public class VerificationCodeService {

    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    static final int MAX_REQUESTS_PER_HOUR = 5;
    private static final Duration HOUR = Duration.ofHours(1);

    private final VerificationCodeRepository repository;
    private final EmailDispatcher dispatcher;
    private final AccountSecurityPolicy policy;
    private final OneTimeCodeHasher hasher;
    private final Clock clock;
    // Uma instância reaproveitada. Nada de getInstanceStrong(): pode travar esperando entropia no Linux
    private final SecureRandom random = new SecureRandom();

    public VerificationCodeService(VerificationCodeRepository repository, EmailDispatcher dispatcher,
                                   AccountSecurityPolicy policy, OneTimeCodeHasher hasher, Clock clock) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.policy = policy;
        this.hasher = hasher;
        this.clock = clock;
    }

    /**
     * Gera um código novo, invalida os anteriores da mesma finalidade e manda o e-mail (depois do commit).
     *
     * @throws CodeRequestLimitException pedido cedo demais ou acima dos limites; nada é enviado (a recusa
     *         não marca a transação de quem chamou para desfazer: ele pode ignorá-la em silêncio)
     */
    @Transactional(noRollbackFor = CodeRequestLimitException.class)
    public void send(User user, CodePurpose purpose, String ip) {
        OffsetDateTime now = now();
        ensureWithinLimits(user.getId(), purpose, ip, now);

        repository.findByUserIdAndPurposeAndConsumedAtIsNull(user.getId(), purpose).forEach(old -> old.consume(now));
        String code = generateCode();
        VerificationCode saved = repository.save(new VerificationCode(user, purpose,
                hasher.tag(purpose, user.getId(), code), now.plus(purpose.validity()), ip));

        dispatcher.dispatch(purpose.template(), user.getEmail(), Map.of(
                "recipientName", user.getFullName(),
                "code", code,
                "validityMinutes", purpose.validity().toMinutes()
        ), "code/" + purpose.name().toLowerCase() + "/" + idOf(saved));
    }

    /**
     * Confere o código e, se certo, consome. Roda numa transação própria: a tentativa errada fica
     * gravada mesmo quando quem chamou recusa a operação e desfaz a dele.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verify(UUID userId, CodePurpose purpose, String code) {
        OffsetDateTime now = now();
        VerificationCode stored = repository.findActiveForUpdate(userId, purpose).orElse(null);
        if (stored == null || !stored.isUsableAt(now)) {
            return false;
        }
        if (code == null || !code.matches("\\d{6}") || !hasher.matches(stored.getCodeHmac(), purpose, userId, code)) {
            stored.registerWrongAttempt();
            return false;
        }
        stored.consume(now);
        return true;
    }

    /** Limpeza diária (AccountMaintenanceJob): códigos vencidos há mais de 7 dias. */
    @Transactional
    public int deleteExpired() {
        return repository.deleteExpiredBefore(now().minusDays(7));
    }

    /** 000000 a 999999, distribuição uniforme. */
    String generateCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    private void ensureWithinLimits(UUID userId, CodePurpose purpose, String ip, OffsetDateTime now) {
        OffsetDateTime latest = repository.findLatestCreatedAt(userId, purpose).orElse(null);
        if (latest != null && latest.plus(RESEND_INTERVAL).isAfter(now)) {
            throw new CodeRequestLimitException(Duration.between(now, latest.plus(RESEND_INTERVAL)));
        }
        if (repository.countByUserIdAndPurposeAndCreatedAtAfter(userId, purpose, now.minus(HOUR)) >= MAX_REQUESTS_PER_HOUR) {
            throw new CodeRequestLimitException(HOUR);
        }
        if (ip != null && repository.countByRequestIpAndCreatedAtAfter(ip, now.minus(HOUR)) >= policy.codeRequestsPerIpPerHour()) {
            throw new CodeRequestLimitException(HOUR);
        }
    }

    // Nos testes unitários o repositório é simulado e o id não é gerado
    private static String idOf(VerificationCode code) {
        return code.getId() == null ? UUID.randomUUID().toString() : code.getId().toString();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
