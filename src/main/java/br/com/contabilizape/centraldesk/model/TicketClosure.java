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

import java.time.OffsetDateTime;
import java.util.UUID;

/** Um encerramento que foi desfeito por uma reabertura (histórico do chamado). */
@Entity
@Table(name = "ticket_closures")
public class TicketClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private User closedBy;

    @Column(name = "closed_at", nullable = false)
    private OffsetDateTime closedAt;

    @Column(name = "resolution", length = 2000)
    private String resolution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reopened_by_id", nullable = false)
    private User reopenedBy;

    @Column(name = "reopened_at", nullable = false)
    private OffsetDateTime reopenedAt;

    @Column(name = "reopen_reason", nullable = false, length = 1000)
    private String reopenReason;

    protected TicketClosure() {
    }

    /** Guarda o encerramento atual do chamado; chamar antes de Ticket.reopen, que limpa esses campos. */
    public TicketClosure(Ticket ticket, User reopenedBy, OffsetDateTime reopenedAt, String reopenReason) {
        this.ticket = ticket;
        this.closedBy = ticket.getClosedBy();
        this.closedAt = ticket.getClosedAt();
        this.resolution = ticket.getResolution();
        this.reopenedBy = reopenedBy;
        this.reopenedAt = reopenedAt;
        this.reopenReason = reopenReason;
    }

    public UUID getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public User getClosedBy() { return closedBy; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public String getResolution() { return resolution; }
    public User getReopenedBy() { return reopenedBy; }
    public OffsetDateTime getReopenedAt() { return reopenedAt; }
    public String getReopenReason() { return reopenReason; }
}
