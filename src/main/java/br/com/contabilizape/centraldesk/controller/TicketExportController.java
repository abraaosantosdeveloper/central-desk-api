package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.TicketExportApi;
import br.com.contabilizape.centraldesk.service.TicketExportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/tickets")
public class TicketExportController implements TicketExportApi {

    static final MediaType TEXT_CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final TicketExportService exportService;
    private final ZoneId zone;

    public TicketExportController(TicketExportService exportService,
                                  @Value("${app.tickets.time-zone}") ZoneId zone) {
        this.exportService = exportService;
        this.zone = zone;
    }

    /** Administradores (tudo) e coordenadores (o próprio setor); os demais, 403 (regra no serviço, lida do banco). */
    @Override
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@AuthenticationPrincipal Jwt jwt) {
        byte[] csv = exportService.exportCsv(UUID.fromString(jwt.getSubject()));
        String fileName = "chamados-" + LocalDate.now(Clock.system(zone)) + ".csv";
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                // Dados internos: o navegador e proxies não guardam cópia
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv);
    }
}
