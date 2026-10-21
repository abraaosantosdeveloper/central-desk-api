package br.com.contabilizape.centraldesk.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "label", nullable = false, unique = true, length = 60)
    private String label;

    // Cor em hexadecimal (#RRGGBB), usada nas etiquetas do front-end
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color", nullable = false, length = 7)
    private String color;

    // Preenchido quando o administrador remove a categoria (os chamados antigos continuam com ela)
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    protected Category() {}

    public Category(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public void update(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public void archive(OffsetDateTime when) {
        this.archivedAt = when;
    }

    public boolean isActive() { return archivedAt == null; }

    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public String getColor() { return color; }
    public OffsetDateTime getArchivedAt() { return archivedAt; }
}
