package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketExportRow;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Formato do CSV da exportação (SCRUM-XXX): cabeçalho, escape, "Em espera", datas no fuso e injeção de fórmula. */
class TicketCsvWriterTest {

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");
    private static final OffsetDateTime OPENED = OffsetDateTime.of(2026, 11, 3, 13, 5, 9, 0, ZoneOffset.UTC);

    private final TicketCsvWriter writer = new TicketCsvWriter(RECIFE);

    @Test
    void startsWithBomAndHeaderEvenWithoutTickets() {
        String csv = csv(List.of());

        assertThat(csv).isEqualTo("﻿" + TicketCsvWriter.HEADER + "\r\n");
        assertThat(TicketCsvWriter.HEADER).isEqualTo("Número;Título;Descrição;Solicitante;Setor de origem;"
                + "Setor de destino;Responsável;Status;Aberto em;Primeiro atendimento em;Encerrado em;Atualizado em");
    }

    @Test
    void writesOneLinePerTicketWithDatesInTheCompanyZone() {
        TicketExportRow row = new TicketExportRow(42L, "Impressora", "Não imprime", "Maria da Silva", "Financeiro",
                "Inovação", "Carlos Lima", TicketStatus.CLOSED, OPENED, OPENED.plusHours(1), OPENED.plusHours(2),
                OPENED.plusHours(2));

        assertThat(lines(csv(List.of(row)))[1]).isEqualTo("42;Impressora;Não imprime;Maria da Silva;Financeiro;"
                + "Inovação;Carlos Lima;Encerrado;03/11/2026 10:05:09;03/11/2026 11:05:09;03/11/2026 12:05:09;"
                + "03/11/2026 12:05:09");
    }

    @Test
    void ticketWithoutAssigneeIsWaitingAndEmptyDatesStayEmpty() {
        TicketExportRow row = new TicketExportRow(7L, "VPN", "Caiu", "João Souza", "Fiscal", "Inovação", null,
                TicketStatus.OPEN, OPENED, null, null, null);

        assertThat(lines(csv(List.of(row)))[1])
                .isEqualTo("7;VPN;Caiu;João Souza;Fiscal;Inovação;Em espera;Aberto;03/11/2026 10:05:09;;;");
    }

    @Test
    void quotesFieldsWithSeparatorQuotesOrLineBreaks() {
        TicketExportRow row = new TicketExportRow(1L, "Erro; urgente", "Linha 1\nLinha 2 com \"aspas\"", "Ana",
                "Fiscal", "Inovação", null, TicketStatus.IN_PROGRESS, OPENED, null, null, null);

        assertThat(csv(List.of(row))).contains(
                "1;\"Erro; urgente\";\"Linha 1\nLinha 2 com \"\"aspas\"\"\";Ana;Fiscal;Inovação;Em espera;Em andamento;");
    }

    @Test
    void neutralizesSpreadsheetFormulas() {
        TicketExportRow row = new TicketExportRow(1L, "=HYPERLINK(\"http://x\")", "+1", "@Ana", "-Fiscal",
                "Inovação", "=cmd", TicketStatus.OPEN, OPENED, null, null, null);

        assertThat(lines(csv(List.of(row)))[1])
                .startsWith("1;\"'=HYPERLINK(\"\"http://x\"\")\";'+1;'@Ana;'-Fiscal;Inovação;'=cmd;");
    }

    private String csv(List<TicketExportRow> rows) {
        return new String(writer.write(rows), StandardCharsets.UTF_8);
    }

    private static String[] lines(String csv) {
        return csv.split("\r\n");
    }
}
