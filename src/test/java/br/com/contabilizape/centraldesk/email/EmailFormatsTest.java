package br.com.contabilizape.centraldesk.email;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Datas no fuso da empresa (o servidor grava em UTC) e texto do usuário quebrado em linhas.
 */
class EmailFormatsTest {

    private final EmailFormats formats = new EmailFormats(ZoneId.of("America/Recife"), 20);

    @Test
    void datesAndTimesAreShownInTheCompanyTimeZone() {
        OffsetDateTime utc = OffsetDateTime.parse("2026-10-23T17:30:00Z");

        assertThat(formats.date(utc)).isEqualTo("23/10/2026");
        assertThat(formats.time(utc)).isEqualTo("14:30");
    }

    @Test
    void lateNightUtcIsStillTheSameDayInRecife() {
        assertThat(formats.date(OffsetDateTime.parse("2026-10-24T02:10:00Z"))).isEqualTo("23/10/2026");
    }

    @Test
    void textIsSplitIntoLinesWhateverTheLineBreak() {
        assertThat(formats.lines("um\r\ndois\ntrês")).containsExactly("um", "dois", "três");
        assertThat(formats.lines(null)).isEmpty();
    }

    @Test
    void longTextIsCutAndFlagged() {
        EmailFormats.Excerpt excerpt = formats.excerpt("123456789\n123456789012345");

        assertThat(String.join("\n", excerpt.lines())).hasSize(20);
        assertThat(excerpt.truncated()).isTrue();
    }

    @Test
    void shortTextIsKeptWhole() {
        EmailFormats.Excerpt excerpt = formats.excerpt("Bom dia!\nTudo certo.");

        assertThat(excerpt.lines()).containsExactly("Bom dia!", "Tudo certo.");
        assertThat(excerpt.truncated()).isFalse();
    }
}
