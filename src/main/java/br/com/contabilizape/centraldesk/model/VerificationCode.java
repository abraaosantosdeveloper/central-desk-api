package br.com.contabilizape.centraldesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Código de 6 dígitos enviado por e-mail. O banco guarda só o HMAC (OneTimeCodeHasher): quem lê a
 * tabela não descobre código nenhum.
 */
@Entity
@Table(name = "verification_codes")
public class VerificationCode {

    /** Tentativas erradas que um código aguenta; depois disso é preciso pedir outro. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private CodePurpose purpose;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code_hmac", nullable = false, length = 64)
    private String codeHmac;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "request_ip", length = 45)
    private String requestIp;

    protected VerificationCode() {
    }

    public VerificationCode(User user, CodePurpose purpose, String codeHmac, OffsetDateTime expiresAt, String requestIp) {
        this.user = user;
        this.purpose = purpose;
        this.codeHmac = codeHmac;
        this.expiresAt = expiresAt;
        this.requestIp = requestIp;
    }

    public boolean isUsableAt(OffsetDateTime now) {
        return consumedAt == null && now.isBefore(expiresAt) && attempts < MAX_ATTEMPTS;
    }

    public void registerWrongAttempt() {
        attempts++;
    }

    /** Uso único: depois de aceito, ou quando um código novo é pedido, este não vale mais. */
    public void consume(OffsetDateTime now) {
        this.consumedAt = now;
    }

    public UUID getId() { return id; }
    public CodePurpose getPurpose() { return purpose; }
    public String getCodeHmac() { return codeHmac; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getRequestIp() { return requestIp; }
}
