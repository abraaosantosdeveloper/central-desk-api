package br.com.contabilizape.centraldesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Uma linha da trilha de auditoria da segurança das contas. Só é inserida, nunca alterada. */
@Entity
@Table(name = "security_events")
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Nulo quando a tentativa foi com um e-mail que não existe. */
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40, updatable = false)
    private SecurityEventType type;

    @Column(name = "ip", length = 45, updatable = false)
    private String ip;

    @Column(name = "user_agent", length = 255, updatable = false)
    private String userAgent;

    @Column(name = "details", length = 500, updatable = false)
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SecurityEvent() {
    }

    public SecurityEvent(UUID userId, SecurityEventType type, String ip, String userAgent, String details) {
        this.userId = userId;
        this.type = type;
        this.ip = ip;
        this.userAgent = userAgent;
        this.details = details;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public SecurityEventType getType() { return type; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public String getDetails() { return details; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
