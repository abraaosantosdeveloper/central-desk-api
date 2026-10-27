package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TrustedDeviceResponse;
import br.com.contabilizape.centraldesk.model.TrustedDevice;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.TrustedDeviceRepository;
import br.com.contabilizape.centraldesk.security.BrowserLabel;
import br.com.contabilizape.centraldesk.security.OpaqueTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Navegadores confiáveis (PLANO-SEGURANCA §9.2 e S7): 30 dias fixos a partir da confirmação do código,
 * no máximo 10 por conta (o mais antigo sai). A revogação em massa (troca de senha, bloqueio...) é feita
 * junto com o fim das sessões, no RefreshTokenService.
 */
@Service
public class TrustedDeviceService {

    public static final Duration VALIDITY = Duration.ofDays(30);
    static final int MAX_PER_ACCOUNT = 10;

    private final TrustedDeviceRepository repository;
    private final Clock clock;

    public TrustedDeviceService(TrustedDeviceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** O cookie é deste usuário e ainda vale? Se sim, registra o uso. */
    @Transactional
    public boolean isTrusted(User user, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        OffsetDateTime now = now();
        return repository.findByTokenHash(OpaqueTokens.sha256(rawToken))
                .filter(device -> device.getUser().getId().equals(user.getId()))
                .filter(device -> device.isActiveAt(now))
                .map(device -> {
                    device.use(now);
                    return true;
                })
                .orElse(false);
    }

    /** Cria a confiança deste navegador e devolve o token em claro (vai só para o cookie). */
    @Transactional
    public String trust(User user, String userAgent) {
        OffsetDateTime now = now();
        List<TrustedDevice> active = repository.findActive(user.getId(), now);
        // Mais novo primeiro: a partir do 10º, os antigos saem para dar lugar ao novo
        active.stream().skip(MAX_PER_ACCOUNT - 1L).forEach(device -> device.revoke(now));

        String rawToken = OpaqueTokens.generate();
        repository.save(new TrustedDevice(user, OpaqueTokens.sha256(rawToken), BrowserLabel.of(userAgent), now.plus(VALIDITY)));
        return rawToken;
    }

    @Transactional(readOnly = true)
    public List<TrustedDeviceResponse> list(UUID userId) {
        return repository.findActive(userId, now()).stream().map(TrustedDeviceResponse::from).toList();
    }

    /** Remove um navegador da própria conta. O de outra pessoa é tratado como inexistente (nada acontece). */
    @Transactional
    public void revoke(UUID userId, UUID deviceId) {
        repository.findById(deviceId)
                .filter(device -> device.getUser().getId().equals(userId))
                .ifPresent(device -> device.revoke(now()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllActiveByUserId(userId, now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
