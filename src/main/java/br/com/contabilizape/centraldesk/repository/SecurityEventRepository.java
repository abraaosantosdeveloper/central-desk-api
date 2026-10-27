package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.SecurityEvent;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {

    long countByUserIdAndTypeAndCreatedAtAfter(UUID userId, SecurityEventType type, OffsetDateTime since);
}
