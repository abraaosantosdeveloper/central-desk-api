package br.com.contabilizape.centraldesk.model;

import br.com.contabilizape.centraldesk.exception.DepartmentRequestAlreadyReviewedException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "department_requests")
public class DepartmentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DepartmentRequestStatus status = DepartmentRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DepartmentRequest() {}

    public DepartmentRequest(User user, Department department) {
        this.user = user;
        this.department = department;
    }

    /** O admin aprova: quem pediu passa para o departamento pedido. */
    public void approve(User reviewer, OffsetDateTime now) {
        review(DepartmentRequestStatus.APPROVED, reviewer, now);
        user.moveTo(department);
    }

    /** O admin recusa: quem pediu continua onde está (no Geral) e pode pedir de novo. */
    public void reject(User reviewer, OffsetDateTime now) {
        review(DepartmentRequestStatus.REJECTED, reviewer, now);
    }

    // A decisão é única; a V9 também exige revisor e data em todo pedido que não está pendente
    private void review(DepartmentRequestStatus decision, User reviewer, OffsetDateTime now) {
        if (status != DepartmentRequestStatus.PENDING) {
            throw new DepartmentRequestAlreadyReviewedException();
        }
        this.status = decision;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Department getDepartment() { return department; }
    public DepartmentRequestStatus getStatus() { return status; }
    public User getReviewedBy() { return reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}