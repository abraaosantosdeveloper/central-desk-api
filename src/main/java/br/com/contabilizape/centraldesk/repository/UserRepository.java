package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.dto.AssigneeResponse;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    /**
     * Login: a linha fica travada até o fim da transação, para que tentativas simultâneas contem uma
     * a uma e o bloqueio aconteça uma vez só.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    boolean existsByCpf(String cpf);

    Optional<User> findByCpf(String cpf);

    /** Faxina diária: cadastros que não confirmaram o e-mail (códigos e auditoria saem junto, ON DELETE CASCADE). */
    @Modifying
    @Query("delete from User u where u.status = :status and u.createdAt < :before")
    int deleteByStatusCreatedBefore(@Param("status") UserStatus status, @Param("before") OffsetDateTime before);

    /** Corte de sessões do usuário (lido a cada requisição autenticada); vazio quando não há corte. */
    @Query("select u.sessionsValidFrom from User u where u.id = :id")
    Optional<OffsetDateTime> findSessionsValidFrom(@Param("id") UUID id);

    @Query("select a.user from Admin a where a.user.status = :status")
    List<User> findAdminsByStatus(@Param("status") UserStatus status);

    /** Administradores do setor de destino (hoje sempre a Inovação; com a SCRUM-148, o setor do chamado). */
    @Query("select a.user from Admin a where a.user.status = :status and a.user.department.id = :departmentId")
    List<User> findAdminsInDepartment(@Param("departmentId") UUID departmentId, @Param("status") UserStatus status);

    /** Quem pode atender chamados (Inovação ou admin, conta ativa), com quantos tem em atendimento. */
    @Query("""
            select new br.com.contabilizape.centraldesk.dto.AssigneeResponse(u.id, u.fullName, u.department.name,
                (select count(t) from Ticket t where t.assignee = u and t.status = :inProgress))
            from User u
            where u.status = :active
              and (u.department.id = :handlerDepartmentId or u.id in (select a.userId from Admin a))
            order by u.fullName
            """)
    List<AssigneeResponse> findAssignees(@Param("handlerDepartmentId") UUID handlerDepartmentId,
                                         @Param("active") UserStatus active,
                                         @Param("inProgress") TicketStatus inProgress);

    /** Listagem do painel do admin: o departamento vem junto, sem uma consulta por conta. */
    @Override
    @EntityGraph(attributePaths = "department")
    Page<User> findAll(Specification<User> spec, Pageable pageable);
}
