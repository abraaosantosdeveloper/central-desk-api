package br.com.contabilizape.centraldesk.email;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Formatação do que vai nos e-mails: datas no fuso da empresa (o servidor grava em UTC) e texto
 * digitado por usuário quebrado em linhas, para virar &lt;br&gt; sem th:utext.
 */
public class EmailFormats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Trecho de um texto longo: as linhas que cabem e se algo ficou de fora. */
    public record Excerpt(List<String> lines, boolean truncated) {
    }

    private final ZoneId zone;
    private final int maxChars;

    public EmailFormats(ZoneId zone, int maxChars) {
        this.zone = zone;
        this.maxChars = maxChars;
    }

    public String date(OffsetDateTime moment) {
        return moment.atZoneSameInstant(zone).format(DATE);
    }

    public String time(OffsetDateTime moment) {
        return moment.atZoneSameInstant(zone).format(TIME);
    }

    public List<String> lines(String text) {
        return text == null || text.isEmpty() ? List.of() : List.of(text.split("\\R", -1));
    }

    /** Até o limite de caracteres (o Gmail corta e-mails grandes; o resto fica no sistema). */
    public Excerpt excerpt(String text) {
        if (text == null || text.length() <= maxChars) {
            return new Excerpt(lines(text), false);
        }
        return new Excerpt(new ArrayList<>(lines(text.substring(0, maxChars))), true);
    }
}
