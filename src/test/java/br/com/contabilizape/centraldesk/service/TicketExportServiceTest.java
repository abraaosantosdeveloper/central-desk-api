package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketExportRow;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.TicketOverviewForbiddenException;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Quem pode exportar e qual recorte recebe (SCRUM-222). O recorte no banco é coberto no TicketExportIntegrationTest. */
@ExtendWith(MockitoExtension.class)
class TicketExportServiceTest {

    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID SECTOR = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");
    private static final TicketExportRow ROW = new TicketExportRow(UUID.randomUUID(), 1L, "Impressora",
            "Não imprime", TicketStatus.OPEN, "Hardware", "Alta", "Maria", "Financeiro", "Inovação", null, null,
            null, null, null, null, OffsetDateTime.now(), null);

    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminRepository adminRepository;

    private TicketExportService service;

    @BeforeEach
    void setUp() {
        service = new TicketExportService(ticketRepository, userRepository, adminRepository, ZoneId.of("America/Recife"));
    }

    @Test
    void adminExportsEveryTicket() throws Exception {
        activeUser();
        when(adminRepository.existsById(USER_ID)).thenReturn(true);
        when(ticketRepository.findAllForExport()).thenReturn(List.of(ROW));

        byte[] spreadsheet = service.exportXlsx(USER_ID);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(spreadsheet))) {
            assertThat(workbook.getSheet("Chamados").getRow(1).getCell(1).getStringCellValue()).isEqualTo("Impressora");
        }
        verify(ticketRepository, never()).findSectorForExport(SECTOR);
    }

    @Test
    void coordinatorExportsOnlyTheOwnSector() {
        activeUser();
        when(adminRepository.existsById(USER_ID)).thenReturn(false);
        when(userRepository.isCoordinator(USER_ID)).thenReturn(true);
        when(ticketRepository.findSectorForExport(SECTOR)).thenReturn(List.of(ROW));

        service.exportXlsx(USER_ID);

        verify(ticketRepository).findSectorForExport(SECTOR);
        verify(ticketRepository, never()).findAllForExport();
    }

    @Test
    void collaboratorIsForbidden() {
        activeUser();
        when(adminRepository.existsById(USER_ID)).thenReturn(false);
        when(userRepository.isCoordinator(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.exportXlsx(USER_ID)).isInstanceOf(TicketOverviewForbiddenException.class);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void deactivatedAccountIsRejected() {
        User user = mock(User.class);
        when(user.isActive()).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.exportXlsx(USER_ID)).isInstanceOf(AccountNotActiveException.class);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void unknownUserIsAnInvalidSession() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportXlsx(USER_ID)).isInstanceOf(InvalidSessionException.class);
    }

    private void activeUser() {
        User user = mock(User.class);
        Department department = mock(Department.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.isActive()).thenReturn(true);
        lenient().when(user.getDepartment()).thenReturn(department);
        lenient().when(department.getId()).thenReturn(SECTOR);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }
}
