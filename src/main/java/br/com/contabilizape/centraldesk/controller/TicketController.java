package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.TicketApi;
import br.com.contabilizape.centraldesk.dto.TicketCreateRequest;
import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/tickets")
public class TicketController implements TicketApi {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    @PostMapping
    public ResponseEntity<TicketResponse> open(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody @Valid TicketCreateRequest request) {
        TicketResponse ticket = ticketService.open(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.created(URI.create("/tickets/" + ticket.id())).body(ticket);
    }
}
