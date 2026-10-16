package br.com.contabilizape.centraldesk.model;

import jakarta.persistence.*;

/**
 * Prioridades fixas, criadas na V4. O id é a ordem de urgência: 1 (Baixa) a 4 (Crítica).
 */
@Entity
@Table(name = "priorities")
public class Priority {

    @Id
    private Short id;

    @Column(name = "label", nullable = false, unique = true, length = 20)
    private String label;

    protected Priority() {}

    public Short getId() { return id; }
    public String getLabel() { return label; }
}
