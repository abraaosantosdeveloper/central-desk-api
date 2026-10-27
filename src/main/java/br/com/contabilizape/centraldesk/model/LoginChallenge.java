package br.com.contabilizape.centraldesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A etapa entre a senha certa e o código (cookie tfa_challenge). Só serve para a conta que acertou a
 * senha nesta tentativa; morre em 5 minutos, no uso ou depois de 5 códigos errados.
 */
@Entity
@Table(name = "login_challenges")
public class LoginChallenge {

    public static final int MAX_FAILED_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "failure_notified", nullable = false)
    private boolean failureNotified;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "ip", length = 45)
    private String ip;

    protected LoginChallenge() {
    }

    public LoginChallenge(User user, String tokenHash, OffsetDateTime expiresAt, String ip) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.ip = ip;
    }

    public boolean isUsableAt(OffsetDateTime now) {
        return consumedAt == null && now.isBefore(expiresAt) && failedAttempts < MAX_FAILED_ATTEMPTS;
    }

    public void registerFailure() {
        failedAttempts++;
    }

    /** true na primeira falha: é quando o dono recebe o aviso. */
    public boolean markFailureNotified() {
        if (failureNotified) {
            return false;
        }
        failureNotified = true;
        return true;
    }

    public void consume(OffsetDateTime now) {
        consumedAt = now;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
}
