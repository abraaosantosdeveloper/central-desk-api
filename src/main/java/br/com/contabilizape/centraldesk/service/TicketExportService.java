package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketExportRow;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.TicketOverviewForbiddenException;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Exportação dos chamados em planilha Excel (SCRUM-222), para análises fora do sistema. O escopo é o mesmo de
 * "Todos os chamados": administrador exporta todos; coordenador, os que o próprio setor abriu ou recebeu;
 * os demais recebem 403. Administrador e coordenador vêm do banco, nunca do token.
 */
@Service
public class TicketExportService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final TicketSpreadsheetWriter spreadsheetWriter;

    public TicketExportService(TicketRepository ticketRepository, UserRepository userRepository,
                               AdminRepository adminRepository,
                               @Value("${app.tickets.time-zone}") ZoneId zone) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.spreadsheetWriter = new TicketSpreadsheetWriter(zone);
    }

    @Transactional(readOnly = true)
    public byte[] exportXlsx(UUID userId) {
        return spreadsheetWriter.write(rowsVisibleTo(findActiveUser(userId)));
    }

    private List<TicketExportRow> rowsVisibleTo(User user) {
        if (adminRepository.existsById(user.getId())) {
            return ticketRepository.findAllForExport();
        }
        if (userRepository.isCoordinator(user.getId())) {
            return ticketRepository.findSectorForExport(user.getDepartment().getId());
        }
        throw new TicketOverviewForbiddenException();
    }

    // O token vale até 15 min: a conta pode ter sido desativada nesse meio-tempo
    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (!user.isActive()) {
            throw new AccountNotActiveException();
        }
        return user;
    }
}
