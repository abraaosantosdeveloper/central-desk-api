package br.com.contabilizape.centraldesk.model;

import br.com.contabilizape.centraldesk.exception.InvalidTicketTransitionException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Número legível (#n), gerado pelo banco (identity, V17) e lido de volta no INSERT; nunca vem do cliente
    @Generated(event = EventType.INSERT)
    @Column(name = "ticket_number", insertable = false, updatable = false, nullable = false)
    private Long number;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    // Coluna TEXT no banco; o limite de tamanho fica no DTO
    @Column(name = "description", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "priority_id", nullable = false)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private User closedBy;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    // O que foi feito, registrado por quem encerrou (SCRUM-174)
    @Column(name = "resolution", length = 2000)
    private String resolution;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected Ticket() {}

    /** Abre o chamado: status OPEN, sem responsável. */
    public Ticket(String title, String description, Category category, Priority priority, User requester) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.requester = requester;
    }

    /** Troca o conteúdo do formulário. Quem pode e quando é decidido no TicketService. */
    public void edit(String title, String description, Category category, Priority priority) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
    }

    public boolean isOpen() {
        return status == TicketStatus.OPEN;
    }

    /** Alguém da Inovação assume o chamado: OPEN → IN_PROGRESS. Quem pode é decidido no SecurityConfig. */
    public void assign(User assignee, OffsetDateTime now) {
        moveTo(TicketStatus.IN_PROGRESS);
        this.assignee = assignee;
        this.assignedAt = now;
    }

    /**
     * Passa o chamado para outra pessoa. Na fila, equivale a assumir em nome dela; em atendimento,
     * só troca o responsável e mantém o assignedAt (o tempo de atendimento conta desde o início).
     */
    public void reassign(User newAssignee, OffsetDateTime now) {
        if (status == TicketStatus.CLOSED) {
            throw new InvalidTicketTransitionException(status, TicketStatus.IN_PROGRESS);
        }
        if (isOpen()) {
            assign(newAssignee, now);
        } else {
            this.assignee = newAssignee;
        }
    }

    /** Encerra o chamado (OPEN ou IN_PROGRESS → CLOSED). O responsável é mantido: é ele quem será avaliado. */
    public void close(User closedBy, String resolution, OffsetDateTime now) {
        moveTo(TicketStatus.CLOSED);
        this.closedBy = closedBy;
        this.closedAt = now;
        this.resolution = resolution;
    }

    /** Devolve à fila um chamado em atendimento (o responsável saiu: conta desativada ou anonimizada). */
    public void returnToQueue() {
        moveTo(TicketStatus.OPEN);
        this.assignee = null;
        this.assignedAt = null;
    }

    // Único ponto que altera o status: a tabela de transições fica em TicketStatus
    private void moveTo(TicketStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTicketTransitionException(status, target);
        }
        this.status = target;
    }

    public UUID getId() { return id; }
    public Long getNumber() { return number; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TicketStatus getStatus() { return status; }
    public Category getCategory() { return category; }
    public Priority getPriority() { return priority; }
    public User getRequester() { return requester; }
    public User getAssignee() { return assignee; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public User getClosedBy() { return closedBy; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public String getResolution() { return resolution; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
