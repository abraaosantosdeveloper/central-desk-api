package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketExportRow;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Monta a planilha Excel (.xlsx) da exportação de chamados (SCRUM-222), já formatada: cabeçalho destacado e
 * congelado, filtro em todas as colunas, larguras definidas, linhas zebradas, datas como data de verdade
 * (ordenáveis e filtráveis) no fuso da empresa e o status colorido.
 *
 * Campo nulo vira célula vazia (o "nulo" do Excel). Todo texto é gravado como texto, nunca como fórmula: um
 * título que começa com "=" aparece como está e não é executado.
 */
public final class TicketSpreadsheetWriter {

    static final String SHEET_NAME = "Chamados";

    // Limite de caracteres de uma célula do Excel
    private static final int MAX_CELL_LENGTH = 32_767;

    private static final byte[] HEADER_FILL = rgb(0x1F, 0x3A, 0x68);
    private static final byte[] WHITE = rgb(0xFF, 0xFF, 0xFF);
    private static final byte[] STRIPE_FILL = rgb(0xF2, 0xF5, 0xF9);
    private static final byte[] BORDER = rgb(0xD0, 0xD7, 0xE1);

    private static final Map<TicketStatus, StatusLook> STATUS = new EnumMap<>(Map.of(
            TicketStatus.OPEN, new StatusLook("Aberto", rgb(0xDB, 0xEA, 0xFE), rgb(0x1E, 0x40, 0xAF)),
            TicketStatus.IN_PROGRESS, new StatusLook("Em andamento", rgb(0xFE, 0xF3, 0xC7), rgb(0x92, 0x40, 0x0E)),
            TicketStatus.CLOSED, new StatusLook("Encerrado", rgb(0xDC, 0xFC, 0xE7), rgb(0x16, 0x65, 0x34))));

    /** Ordem, título, largura (em caracteres), tipo e valor de cada coluna. */
    static final List<Column> COLUMNS = List.of(
            new Column("Número", 10, Kind.NUMBER, TicketExportRow::number),
            new Column("Título", 40, Kind.TEXT, TicketExportRow::title),
            new Column("Descrição", 60, Kind.TEXT, TicketExportRow::description),
            new Column("Status", 15, Kind.STATUS, TicketExportRow::status),
            new Column("Categoria", 18, Kind.TEXT, TicketExportRow::category),
            new Column("Prioridade", 12, Kind.TEXT, TicketExportRow::priority),
            new Column("Solicitante", 28, Kind.TEXT, TicketExportRow::requesterName),
            new Column("Setor de origem", 20, Kind.TEXT, TicketExportRow::originDepartment),
            new Column("Setor de destino", 20, Kind.TEXT, TicketExportRow::targetDepartment),
            new Column("Responsável", 28, Kind.TEXT, TicketExportRow::assigneeName),
            new Column("Aberto em", 17, Kind.DATE, TicketExportRow::createdAt),
            new Column("Atribuído em", 17, Kind.DATE, TicketExportRow::assignedAt),
            new Column("Primeiro atendimento em", 17, Kind.DATE, TicketExportRow::firstAssignedAt),
            new Column("Encerrado por", 28, Kind.TEXT, TicketExportRow::closedByName),
            new Column("Encerrado em", 17, Kind.DATE, TicketExportRow::closedAt),
            new Column("Resolução", 60, Kind.TEXT, TicketExportRow::resolution),
            new Column("Atualizado em", 17, Kind.DATE, TicketExportRow::updatedAt),
            new Column("ID", 38, Kind.TEXT, row -> row.id() == null ? null : row.id().toString()));

    private final ZoneId zone;

    public TicketSpreadsheetWriter(ZoneId zone) {
        this.zone = zone;
    }

    public byte[] write(List<TicketExportRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            XSSFSheet sheet = workbook.createSheet(SHEET_NAME);

            writeHeader(sheet, styles);
            for (int i = 0; i < rows.size(); i++) {
                writeRow(sheet.createRow(i + 1), rows.get(i), styles, i % 2 == 1);
            }

            for (int c = 0; c < COLUMNS.size(); c++) {
                sheet.setColumnWidth(c, COLUMNS.get(c).width() * 256);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(rows.size(), 1), 0, COLUMNS.size() - 1));

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gerar a planilha de chamados.", e);
        }
    }

    private static void writeHeader(XSSFSheet sheet, Styles styles) {
        XSSFRow header = sheet.createRow(0);
        header.setHeightInPoints(24);
        for (int c = 0; c < COLUMNS.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(COLUMNS.get(c).title());
            cell.setCellStyle(styles.header());
        }
    }

    private void writeRow(XSSFRow row, TicketExportRow ticket, Styles styles, boolean striped) {
        row.setHeightInPoints(18);
        for (int c = 0; c < COLUMNS.size(); c++) {
            Column column = COLUMNS.get(c);
            Object value = column.value().apply(ticket);
            Cell cell = row.createCell(c);
            switch (column.kind()) {
                case NUMBER -> {
                    if (value != null) cell.setCellValue(((Number) value).doubleValue());
                    cell.setCellStyle(styles.body(Kind.NUMBER, striped));
                }
                case DATE -> {
                    if (value != null) cell.setCellValue(((OffsetDateTime) value).atZoneSameInstant(zone).toLocalDateTime());
                    cell.setCellStyle(styles.body(Kind.DATE, striped));
                }
                case STATUS -> {
                    TicketStatus status = (TicketStatus) value;
                    if (status != null) {
                        cell.setCellValue(STATUS.get(status).label());
                        cell.setCellStyle(styles.status(status));
                    } else {
                        cell.setCellStyle(styles.body(Kind.TEXT, striped));
                    }
                }
                case TEXT -> {
                    if (value != null) cell.setCellValue(truncate((String) value));
                    cell.setCellStyle(styles.body(Kind.TEXT, striped));
                }
            }
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_CELL_LENGTH ? value : value.substring(0, MAX_CELL_LENGTH);
    }

    private static byte[] rgb(int r, int g, int b) {
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }

    enum Kind { NUMBER, TEXT, DATE, STATUS }

    record Column(String title, int width, Kind kind, Function<TicketExportRow, Object> value) {
    }

    private record StatusLook(String label, byte[] fill, byte[] font) {
    }

    /** Estilos criados uma vez por planilha (o Excel limita a quantidade de estilos por arquivo). */
    private static final class Styles {

        private final XSSFWorkbook workbook;
        private final XSSFCellStyle header;
        private final Map<String, XSSFCellStyle> body = new HashMap<>();
        private final Map<TicketStatus, XSSFCellStyle> status = new EnumMap<>(TicketStatus.class);

        Styles(XSSFWorkbook workbook) {
            this.workbook = workbook;
            this.header = bordered();
            XSSFFont font = workbook.createFont();
            font.setBold(true);
            font.setColor(new XSSFColor(WHITE, null));
            header.setFont(font);
            header.setFillForegroundColor(new XSSFColor(HEADER_FILL, null));
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setWrapText(true);
        }

        XSSFCellStyle header() {
            return header;
        }

        XSSFCellStyle body(Kind kind, boolean striped) {
            return body.computeIfAbsent(kind + ":" + striped, key -> {
                XSSFCellStyle style = bordered();
                if (striped) {
                    style.setFillForegroundColor(new XSSFColor(STRIPE_FILL, null));
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                if (kind == Kind.NUMBER) {
                    style.setAlignment(HorizontalAlignment.CENTER);
                    style.setDataFormat(workbook.createDataFormat().getFormat("0"));
                }
                if (kind == Kind.DATE) {
                    style.setAlignment(HorizontalAlignment.CENTER);
                    style.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
                }
                return style;
            });
        }

        XSSFCellStyle status(TicketStatus value) {
            return status.computeIfAbsent(value, key -> {
                StatusLook look = STATUS.get(key);
                XSSFCellStyle style = bordered();
                style.setAlignment(HorizontalAlignment.CENTER);
                style.setFillForegroundColor(new XSSFColor(look.fill(), null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                XSSFFont font = workbook.createFont();
                font.setBold(true);
                font.setColor(new XSSFColor(look.font(), null));
                style.setFont(font);
                return style;
            });
        }

        private XSSFCellStyle bordered() {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            XSSFColor color = new XSSFColor(BORDER, null);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setTopBorderColor(color);
            style.setBottomBorderColor(color);
            style.setLeftBorderColor(color);
            style.setRightBorderColor(color);
            return style;
        }
    }
}
