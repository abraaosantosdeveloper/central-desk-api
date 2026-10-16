package br.com.contabilizape.centraldesk.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    protected Category() {}

    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public String getColor() { return color; }
}
