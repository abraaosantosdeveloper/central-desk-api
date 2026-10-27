package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.model.RefreshToken;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.realtime.SessionsEndedEvent;
import br.com.contabilizape.centraldesk.repository.RefreshTokenRepository;
import br.com.contabilizape.centraldesk.repository.TrustedDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Emite, rotaciona e revoga refresh tokens. O valor em claro só existe no cookie do navegador;
 * o banco guarda o hash SHA-256.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration ttl;
    private final ApplicationEventPublisher events;
    private final TrustedDeviceRepository trustedDevices;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               Clock clock,
                               @Value("${app.security.refresh-token.ttl}") Duration ttl,
                               ApplicationEventPublisher events,
                               TrustedDeviceRepository trustedDevices) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = ttl;
        this.events = events;
        this.trustedDevices = trustedDevices;
    }

    /** Cria uma sessão nova e devolve o token em claro (vai só para o cookie). */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(user, hash(rawToken), now().plus(ttl)));
        return rawToken;
    }

    /**
     * Troca o token apresentado por um novo. As exceções não desfazem a transação:
     * a revogação feita antes de recusar (reuso, conta inativa) precisa ficar gravada.
     */
    @Transactional(noRollbackFor = {InvalidSessionException.class, AccountNotActiveException.class})
    public RotatedRefreshToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidSessionException();
        }
        RefreshToken current = repository.findByTokenHash(hash(rawToken)).orElseThrow(InvalidSessionException::new);
        User user = current.getUser();
        OffsetDateTime now = now();

        if (current.isRevoked()) {
            // Token já trocado ou encerrado voltando: provável roubo. Derruba todas as sessões do usuário.
            repository.revokeAllActiveByUserId(user.getId(), now);
            log.warn("Refresh token revogado reapresentado; sessões do usuário {} encerradas", user.getId());
            throw new InvalidSessionException();
        }
        if (current.isExpiredAt(now)) {
            throw new InvalidSessionException();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            repository.revokeAllActiveByUserId(user.getId(), now);
            throw new AccountNotActiveException();
        }

        current.revoke(now);
        return new RotatedRefreshToken(user, issue(user));
    }

    /** Encerra a sessão do token (logout). Token desconhecido ou já revogado: nada a fazer. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(now()));
    }

    /** Encerra todas as sessões do usuário (desativação, anonimização); os navegadores confiáveis caem junto. */
    @Transactional
    public void revokeAll(UUID userId) {
        OffsetDateTime now = now();
        repository.revokeAllActiveByUserId(userId, now);
        trustedDevices.revokeAllActiveByUserId(userId, now);
    }

    /**
     * Encerra todas as sessões NA HORA (troca de senha, e-mail de login trocado pelo admin, bloqueio): além dos
     * refresh tokens, grava o corte que faz os access tokens em uso serem recusados e avisa os streams
     * em tempo real abertos (depois do commit). Tokens emitidos a seguir, na mesma transação, valem.
     */
    @Transactional
    public void endAllSessions(User user) {
        OffsetDateTime now = now();
        user.endSessions(now);
        repository.revokeAllActiveByUserId(user.getId(), now);
        // Nenhum navegador continua dispensado do código (B9)
        trustedDevices.revokeAllActiveByUserId(user.getId(), now);
        events.publishEvent(new SessionsEndedEvent(user.getId()));
    }

    public Duration ttl() {
        return ttl;
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
