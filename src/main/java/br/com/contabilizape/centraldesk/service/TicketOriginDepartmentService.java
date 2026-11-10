package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.DepartmentNotFoundException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.OriginDepartmentChangeForbiddenException;
import br.com.contabilizape.centraldesk.exception.OriginDepartmentConflictException;
import br.com.contabilizape.centraldesk.exception.TicketNotFoundException;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Correção do setor de origem do chamado: somente administradores (revisão da D2, que tornava a origem imutável).
 * A rota já é restrita a ROLE_ADMIN no SecurityConfig; aqui a condição é conferida de novo no banco, porque o
 * token vale até 15 min e a pessoa pode ter deixado de ser admin nesse meio-tempo.
 * Vale em qualquer status: é um ajuste de cadastro (inclusive em encerrados, para as métricas por setor).
 */
@Service
public class TicketOriginDepartmentService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final DepartmentRepository departmentRepository;

    public TicketOriginDepartmentService(TicketRepository ticketRepository, UserRepository userRepository,
                                         AdminRepository adminRepository, DepartmentRepository departmentRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public TicketResponse changeOriginDepartment(UUID ticketId, UUID userId, UUID departmentId) {
        findActiveUser(userId);
        if (!adminRepository.existsById(userId)) {
            throw new OriginDepartmentChangeForbiddenException();
        }
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
        if (ticket.getOriginDepartment().getId().equals(department.getId())) {
            throw new OriginDepartmentConflictException();
        }

        ticket.changeOriginDepartment(department);
        ticketRepository.flush(); // grava agora e atualiza o updated_at
        return TicketResponse.from(ticket);
    }

    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }
        return user;
    }
}
