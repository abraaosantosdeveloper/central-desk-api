package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketExportRow;
import br.com.contabilizape.centraldesk.model.TicketStatus;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Monta o CSV da exportação de chamados (SCRUM-XXX) no formato que o Excel em português abre direto:
 * UTF-8 com BOM (acentos certos), ";" como separador e quebra de linha CRLF (RFC 4180). Campos com ";", aspas
 * ou quebra de linha vão entre aspas, com as aspas internas dobradas.
 *
 * Injeção de fórmula (CSV injection): título, descrição e nomes são texto livre de usuários. Um campo que
 * começa com =, +, -, @, tabulação ou retorno de carro ganha um apóstrofo na frente, para a planilha
 * mostrar o texto em vez de executá-lo.
 */
public final class TicketCsvWriter {

    public static final String WAITING = "Em espera";

    static final String HEADER = String.join(";",
            "Número", "Título", "Descrição", "Solicitante", "Setor de origem", "Setor de destino", "Responsável",
            "Status", "Aberto em", "Primeiro atendimento em", "Encerrado em", "Atualizado em");

    private static final char SEPARATOR = ';';
    private static final String LINE_BREAK = "\r\n";
    private static final String BOM = "﻿";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Map<TicketStatus, String> STATUS_LABELS = Map.of(
            TicketStatus.OPEN, "Aberto",
            TicketStatus.IN_PROGRESS, "Em andamento",
            TicketStatus.CLOSED, "Encerrado");

    private final ZoneId zone;

    public TicketCsvWriter(ZoneId zone) {
        this.zone = zone;
    }

    public byte[] write(List<TicketExportRow> rows) {
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append(LINE_BREAK);
        for (TicketExportRow row : rows) {
            appendRow(csv, row);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendRow(StringBuilder csv, TicketExportRow row) {
        String[] cells = {
                row.number() == null ? "" : row.number().toString(),
                text(row.title()),
                text(row.description()),
                text(row.requesterName()),
                text(row.originDepartment()),
                text(row.targetDepartment()),
                row.assigneeName() == null ? WAITING : text(row.assigneeName()),
                row.status() == null ? "" : STATUS_LABELS.get(row.status()),
                date(row.createdAt()),
                date(row.firstAssignedAt()),
                date(row.closedAt()),
                date(row.updatedAt())
        };
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(quoteIfNeeded(cells[i]));
        }
        csv.append(LINE_BREAK);
    }

    // Texto livre: neutraliza fórmulas antes de escapar
    private static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        boolean formula = first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
        return formula ? "'" + value : value;
    }

    private String date(OffsetDateTime value) {
        return value == null ? "" : value.atZoneSameInstant(zone).format(DATE_TIME);
    }

    private static String quoteIfNeeded(String value) {
        boolean needsQuotes = value.indexOf(SEPARATOR) >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        return needsQuotes ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}
