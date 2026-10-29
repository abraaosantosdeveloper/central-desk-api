package br.com.contabilizape.centraldesk.model;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Coordenador de setor (SCRUM-219): acompanha os chamados que o próprio departamento abriu ou recebeu.
 * O setor coordenado é o departamento atual da conta; trocar de departamento tira o papel.
 */
@Getter
@Entity
@Table(name = "coordinators")
public class Coordinator {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_id")
    private User grantedBy;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private OffsetDateTime grantedAt;

    protected Coordinator() {}

    public Coordinator(User user, User grantedBy) {
        this.user = user;
        this.grantedBy = grantedBy;
    }
}
