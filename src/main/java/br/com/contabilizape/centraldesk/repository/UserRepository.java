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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    boolean existsByCpf(String cpf);

    @Query("select a.user from Admin a where a.user.status = :status")
    List<User> findAdminsByStatus(@Param("status") UserStatus status);

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
