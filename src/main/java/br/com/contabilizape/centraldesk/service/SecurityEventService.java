package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.model.SecurityEvent;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.SecurityEventRepository;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Trilha de auditoria da segurança das contas (security_events): tentativas, bloqueios, redefinições.
 * Grava na transação de quem chama; os fluxos que recusam o acesso não desfazem a transação
 * (noRollbackFor), então a tentativa recusada fica registrada.
 */
@Service
public class SecurityEventService {

    private static final int MAX_DETAILS = 500;

    private final SecurityEventRepository repository;

    public SecurityEventService(SecurityEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public long countByIpSince(String ip, SecurityEventType type, OffsetDateTime since) {
        return ip == null ? 0 : repository.countByIpAndTypeAndCreatedAtAfter(ip, type, since);
    }

    @Transactional(readOnly = true)
    public long countByUserSince(User user, SecurityEventType type, OffsetDateTime since) {
        return repository.countByUserIdAndTypeAndCreatedAtAfter(user.getId(), type, since);
    }

    @Transactional
    public void record(SecurityEventType type, User user, ClientInfo client, String details) {
        String trimmed = details == null || details.length() <= MAX_DETAILS ? details : details.substring(0, MAX_DETAILS);
        repository.save(new SecurityEvent(user == null ? null : user.getId(), type,
                client == null ? null : client.ip(), client == null ? null : client.userAgent(), trimmed));
    }
}
