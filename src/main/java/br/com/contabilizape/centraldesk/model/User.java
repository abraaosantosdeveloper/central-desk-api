package br.com.contabilizape.centraldesk.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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

    /** Corte de sessões: access tokens emitidos antes disso são recusados (SessionCutoffValidator). */
    @Column(name = "sessions_valid_from")
    private OffsetDateTime sessionsValidFrom;

    @Column(name = "failed_login_attempts", nullable = false)
    private short failedLoginAttempts;

    @Column(name = "blocked_at")
    private OffsetDateTime blockedAt;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = true;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "temporary_password_expires_at")
    private OffsetDateTime temporaryPasswordExpiresAt;

    /** Departamento escolhido no cadastro, até o e-mail ser confirmado (o pedido da Inovação nasce depois). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_department_id")
    private Department requestedDepartment;

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

    public OffsetDateTime getSessionsValidFrom() {
        return sessionsValidFrom;
    }

    public OffsetDateTime getDeactivatedAt() {
        return this.deactivatedAt;
    }

    /**
     * Desativação pelo admin (ex.: desligamento). Bloqueia o login; a conta pode ser reativada.
     * Vale também para a conta bloqueada (a pessoa pode sair da empresa enquanto está bloqueada).
     */
    public void deactivate(OffsetDateTime now) {
        if (this.status != UserStatus.ACTIVE && this.status != UserStatus.BLOCKED) {
            throw new IllegalStateException("Só contas ativas ou bloqueadas podem ser desativadas; status atual: " + this.status);
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

    /**
     * Encerra na hora as sessões abertas: os access tokens emitidos antes deste segundo deixam de valer.
     * Truncado no segundo porque o "iat" do JWT não tem fração; os tokens emitidos a partir daqui valem.
     */
    public void endSessions(OffsetDateTime now) {
        this.sessionsValidFrom = now.truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * Senha nova escolhida pelo dono (perfil, "esqueci minha senha" ou troca obrigatória). Recebe o hash
     * já calculado; a entidade não conhece o algoritmo. Encerra a senha temporária e zera as falhas.
     */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangeRequired = false;
        this.temporaryPasswordExpiresAt = null;
        this.failedLoginAttempts = 0;
    }

    // ---------- segurança da conta (SCRUM-200) ----------

    /** Cadastro novo: só entra depois de confirmar o código enviado ao e-mail. */
    public void awaitEmailConfirmation() {
        this.status = UserStatus.PENDING_VERIFICATION;
        this.emailVerifiedAt = null;
    }

    /** Cadastro pendente refeito (mesmo e-mail ou CPF): quem tem acesso à caixa de e-mail ganha (B3). */
    public void replacePendingRegistration(String fullName, String cpf, String email, String passwordHash,
                                           Department department) {
        if (this.status != UserStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Só cadastros pendentes podem ser refeitos; status atual: " + this.status);
        }
        this.fullName = fullName;
        this.cpf = cpf;
        this.email = email;
        this.passwordHash = passwordHash;
        this.department = department;
        this.requestedDepartment = null;
    }

    public void confirmEmail(OffsetDateTime now) {
        if (this.status != UserStatus.PENDING_VERIFICATION) {
            throw new IllegalStateException("Só cadastros pendentes confirmam o e-mail; status atual: " + this.status);
        }
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = now;
    }

    /** Guarda o departamento pedido até a confirmação do e-mail. */
    public void requestDepartment(Department department) {
        this.requestedDepartment = department;
    }

    /** Devolve e esquece o departamento pedido (o pedido da Inovação é criado na confirmação). */
    public Department takeRequestedDepartment() {
        Department requested = this.requestedDepartment;
        this.requestedDepartment = null;
        return requested;
    }

    /**
     * Conta uma tentativa errada (senha ou código de acesso). Na {@code max}-ésima seguida, bloqueia.
     *
     * @return true se esta falha bloqueou a conta
     */
    public boolean registerFailedLogin(OffsetDateTime now, int max) {
        if (this.status != UserStatus.ACTIVE) {
            throw new IllegalStateException("Só contas ativas contam tentativas; status atual: " + this.status);
        }
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= max) {
            this.status = UserStatus.BLOCKED;
            this.blockedAt = now;
            return true;
        }
        return false;
    }

    /** Acesso completo (senha e, quando exigido, o código): as falhas anteriores deixam de contar. */
    public void resetFailedLogins() {
        this.failedLoginAttempts = 0;
    }

    /**
     * Senha temporária gerada pelo sistema a pedido de um administrador: desbloqueia, zera as falhas e
     * obriga a troca no primeiro acesso. Recebe o hash já calculado.
     */
    public void resetPasswordByAdmin(String temporaryPasswordHash, OffsetDateTime expiresAt) {
        if (this.status != UserStatus.ACTIVE && this.status != UserStatus.BLOCKED) {
            throw new IllegalStateException("Só contas ativas ou bloqueadas têm a senha redefinida; status atual: " + this.status);
        }
        this.status = UserStatus.ACTIVE;
        this.blockedAt = null;
        this.failedLoginAttempts = 0;
        this.passwordHash = temporaryPasswordHash;
        this.passwordChangeRequired = true;
        this.temporaryPasswordExpiresAt = expiresAt;
    }

    public boolean isTemporaryPasswordExpiredAt(OffsetDateTime now) {
        return passwordChangeRequired && temporaryPasswordExpiresAt != null && !now.isBefore(temporaryPasswordExpiresAt);
    }

    public void enableTwoFactor() {
        this.twoFactorEnabled = true;
    }

    public void disableTwoFactor() {
        this.twoFactorEnabled = false;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public OffsetDateTime getBlockedAt() {
        return blockedAt;
    }

    public OffsetDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public OffsetDateTime getTemporaryPasswordExpiresAt() {
        return temporaryPasswordExpiresAt;
    }

    public boolean isBlocked() {
        return this.status == UserStatus.BLOCKED;
    }

    public boolean isPendingVerification() {
        return this.status == UserStatus.PENDING_VERIFICATION;
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
