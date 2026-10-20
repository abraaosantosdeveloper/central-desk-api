package br.com.contabilizape.centraldesk.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
        
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)    
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "cpf", unique = true, length = 11)
    private String cpf;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "validated_at")
    private OffsetDateTime validatedAt;

    @Column(name = "deactivated_at")
    private OffsetDateTime deactivatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected User() {}
    
    public User(String fullName, String cpf, String email, String passwordHash, Department department){
        this.fullName = fullName;
        this.cpf = cpf;
        this.email = email;
        this.passwordHash = passwordHash;
        this.department = department;
    }

    public UUID getId(){
        return this.id;
    }

    public String getFullName(){
        return this.fullName;
    }

    public String getEmail(){
        return this.email;
    }

    public String getCpf(){
        return this.cpf;
    }

    public String getPasswordHash(){
        return this.passwordHash;
    }

    public UserStatus getStatus(){
        return this.status;
    }

    public Department getDepartment(){
        return this.department;
    }

    public OffsetDateTime getCreatedAt(){
        return this.createdAt;
    }

    /** Edição do próprio perfil: só o nome muda. */
    public void rename(String fullName) {
        this.fullName = fullName;
    }

    /** Recebe o hash já calculado; a entidade não conhece o algoritmo. */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }
}
