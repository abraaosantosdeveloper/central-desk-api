package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketExportRow;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planilha da exportação (SCRUM-222): todas as colunas do chamado, nulos como célula vazia, datas como data no fuso
 * da empresa, texto nunca vira fórmula e a formatação (cabeçalho, filtro, painel congelado, status colorido).
 */
class TicketSpreadsheetWriterTest {

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");
    private static final OffsetDateTime OPENED = OffsetDateTime.of(2026, 11, 3, 13, 5, 0, 0, ZoneOffset.UTC);
    private static final UUID ID = UUID.fromString("cccccccc-0000-4000-8000-000000000003");

    private final TicketSpreadsheetWriter writer = new TicketSpreadsheetWriter(RECIFE);

    @Test
    void headerHasEveryTicketField() throws IOException {
        XSSFSheet sheet = sheet(List.of());

        assertThat(texts(sheet.getRow(0))).containsExactly("Número", "Título", "Descrição", "Status", "Categoria",
                "Prioridade", "Solicitante", "Setor de origem", "Setor de destino", "Responsável", "Aberto em",
                "Atribuído em", "Primeiro atendimento em", "Encerrado por", "Encerrado em", "Resolução",
                "Atualizado em", "ID");
        assertThat(sheet.getLastRowNum()).isZero();
    }

    @Test
    void closedTicketFillsEveryColumnWithDatesInTheCompanyZone() throws IOException {
        TicketExportRow row = new TicketExportRow(ID, 42L, "Impressora", "Não imprime", TicketStatus.CLOSED,
                "Hardware", "Alta", "Maria da Silva", "Financeiro", "Inovação", "Carlos Lima",
                OPENED.plusHours(1), OPENED.plusHours(1), "Carlos Lima", OPENED.plusHours(2), "Troquei o toner",
                OPENED, OPENED.plusHours(2));

        XSSFRow line = sheet(List.of(row)).getRow(1);

        assertThat(line.getCell(0).getNumericCellValue()).isEqualTo(42d);
        assertThat(texts(line).subList(1, 10)).containsExactly("Impressora", "Não imprime", "Encerrado", "Hardware",
                "Alta", "Maria da Silva", "Financeiro", "Inovação", "Carlos Lima");
        assertThat(line.getCell(10).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 11, 3, 10, 5));
        assertThat(line.getCell(11).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 11, 3, 11, 5));
        assertThat(line.getCell(12).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 11, 3, 11, 5));
        assertThat(line.getCell(13).getStringCellValue()).isEqualTo("Carlos Lima");
        assertThat(line.getCell(14).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 11, 3, 12, 5));
        assertThat(line.getCell(15).getStringCellValue()).isEqualTo("Troquei o toner");
        assertThat(line.getCell(16).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.of(2026, 11, 3, 12, 5));
        assertThat(line.getCell(17).getStringCellValue()).isEqualTo(ID.toString());
        assertThat(DateUtil.isCellDateFormatted(line.getCell(10))).isTrue();
    }

    // Sem responsável, sem encerramento: as células ficam vazias (o nulo do Excel)
    @Test
    void missingValuesBecomeEmptyCells() throws IOException {
        TicketExportRow row = new TicketExportRow(ID, 7L, "VPN", "Caiu", TicketStatus.OPEN, "Rede", "Baixa",
                "João Souza", "Fiscal", "Inovação", null, null, null, null, null, null, OPENED, null);

        XSSFRow line = sheet(List.of(row)).getRow(1);

        for (int column : new int[]{9, 11, 12, 13, 14, 15, 16}) {
            assertThat(line.getCell(column).getCellType()).as("coluna %d", column).isEqualTo(CellType.BLANK);
        }
        assertThat(line.getCell(3).getStringCellValue()).isEqualTo("Aberto");
    }

    @Test
    void textIsNeverAFormula() throws IOException {
        TicketExportRow row = new TicketExportRow(ID, 1L, "=HYPERLINK(\"http://x\")", "+1", TicketStatus.IN_PROGRESS,
                "Rede", "Média", "@Ana", "Fiscal", "Inovação", "-Carlos", OPENED, OPENED, null, null, null, OPENED, OPENED);

        XSSFRow line = sheet(List.of(row)).getRow(1);

        assertThat(line.getCell(1).getCellType()).isEqualTo(CellType.STRING);
        assertThat(line.getCell(1).getStringCellValue()).isEqualTo("=HYPERLINK(\"http://x\")");
        assertThat(line.getCell(3).getStringCellValue()).isEqualTo("Em andamento");
    }

    @Test
    void comesFormattedWithFrozenHeaderFilterAndColoredStatus() throws IOException {
        TicketExportRow open = new TicketExportRow(ID, 1L, "A", "B", TicketStatus.OPEN, "Rede", "Baixa", "Ana",
                "Fiscal", "Inovação", null, null, null, null, null, null, OPENED, null);
        TicketExportRow closed = new TicketExportRow(ID, 2L, "C", "D", TicketStatus.CLOSED, "Rede", "Baixa", "Ana",
                "Fiscal", "Inovação", "Carlos", OPENED, OPENED, "Carlos", OPENED, "Feito", OPENED, OPENED);

        XSSFSheet sheet = sheet(List.of(open, closed));

        assertThat(sheet.getSheetName()).isEqualTo("Chamados");
        assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
        assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:R3");
        XSSFCellStyle header = sheet.getRow(0).getCell(0).getCellStyle();
        assertThat(header.getFont().getBold()).isTrue();
        assertThat(sheet.getRow(1).getCell(3).getCellStyle().getFillForegroundColorColor())
                .isNotEqualTo(sheet.getRow(2).getCell(3).getCellStyle().getFillForegroundColorColor());
        assertThat(sheet.getColumnWidth(2)).isEqualTo(60 * 256);
    }

    private XSSFSheet sheet(List<TicketExportRow> rows) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(writer.write(rows)));
        return workbook.getSheet("Chamados");
    }

    private static List<String> texts(XSSFRow row) {
        return java.util.stream.IntStream.range(0, row.getLastCellNum())
                .mapToObj(row::getCell)
                .map(TicketSpreadsheetWriterTest::text)
                .toList();
    }

    private static String text(XSSFCell cell) {
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : "";
    }
}
