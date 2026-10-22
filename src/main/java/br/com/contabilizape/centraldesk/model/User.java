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

    public OffsetDateTime getDeletedAt() {
        return this.deletedAt;
    }

    public OffsetDateTime getDeactivatedAt() {
        return this.deactivatedAt;
    }

    /** Desativação pelo admin (ex.: desligamento). Bloqueia o login; a conta pode ser reativada. */
    public void deactivate(OffsetDateTime now) {
        if (this.status != UserStatus.ACTIVE) {
            throw new IllegalStateException("Só contas ativas podem ser desativadas; status atual: " + this.status);
        }
        this.status = UserStatus.DEACTIVATED;
        this.deactivatedAt = now;
    }

    /** Volta a liberar o login. Conta anonimizada não volta: os dados pessoais já foram apagados. */
    public void reactivate() {
        if (this.status != UserStatus.DEACTIVATED) {
            throw new IllegalStateException("Só contas desativadas podem ser reativadas; status atual: " + this.status);
        }
        this.status = UserStatus.ACTIVE;
        this.deactivatedAt = null;
    }

    /** Troca de departamento (pedido aprovado). As permissões mudam na próxima renovação da sessão. */
    public void moveTo(Department department) {
        this.department = department;
    }

    /** Edição do próprio perfil: só o nome muda. */
    public void rename(String fullName) {
        this.fullName = fullName;
    }

    /** Correção feita pelo administrador. Recebe só os 11 dígitos, já validados. */
    public void changeCpf(String cpf) {
        this.cpf = cpf;
    }

    /** Correção feita pelo administrador. O e-mail é o login; chega já normalizado (minúsculas, sem espaços). */
    public void changeEmail(String email) {
        this.email = email;
    }

    /** Recebe o hash já calculado; a entidade não conhece o algoritmo. */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    /**
     * Encerramento de conta (LGPD): apaga os dados pessoais e mantém o registro, para preservar
     * o histórico dos chamados. O e-mail fica único e num domínio que não recebe mensagens.
     */
    public void anonymize(String randomPasswordHash, OffsetDateTime now) {
        this.fullName = "Usuário removido";
        this.cpf = null;
        this.email = "anonimizado-" + this.id + "@anonimizado.invalid";
        this.passwordHash = randomPasswordHash;
        this.status = UserStatus.ANONYMIZED;
        this.deletedAt = now;
    }
}
