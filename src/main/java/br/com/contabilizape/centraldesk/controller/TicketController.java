package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.TicketApi;
import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.TicketCreateRequest;
import br.com.contabilizape.centraldesk.dto.TicketFilter;
import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.dto.TicketSummaryResponse;
import br.com.contabilizape.centraldesk.dto.TicketUpdateRequest;
import br.com.contabilizape.centraldesk.security.TokenService;
import br.com.contabilizape.centraldesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;
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

    // Rotas literais têm prioridade sobre /{id} no Spring MVC

    @Override
    @GetMapping("/mine")
    public PageResponse<TicketSummaryResponse> listMine(@AuthenticationPrincipal Jwt jwt, TicketFilter filter,
                                                        @PageableDefault(size = 20) Pageable pageable) {
        return ticketService.listMine(UUID.fromString(jwt.getSubject()), filter, pageable);
    }

    /** Só Inovação e administradores (regra no SecurityConfig). */
    @Override
    @GetMapping("/queue")
    public PageResponse<TicketSummaryResponse> listQueue(@AuthenticationPrincipal Jwt jwt, TicketFilter filter,
                                                         @PageableDefault(size = 20) Pageable pageable) {
        return ticketService.listQueue(UUID.fromString(jwt.getSubject()), filter, pageable);
    }

    /** Só Inovação e administradores (regra no SecurityConfig). */
    @Override
    @GetMapping("/assigned")
    public PageResponse<TicketSummaryResponse> listAssigned(@AuthenticationPrincipal Jwt jwt, TicketFilter filter,
                                                            @PageableDefault(size = 20) Pageable pageable) {
        return ticketService.listAssigned(UUID.fromString(jwt.getSubject()), filter, pageable);
    }

    /** Só Inovação e administradores (regra no SecurityConfig). */
    @Override
    @PatchMapping("/{id}/assign")
    public TicketResponse assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ticketService.assign(id, UUID.fromString(jwt.getSubject()));
    }

    @Override
    @GetMapping("/{id}")
    public TicketResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        List<String> roles = Objects.requireNonNullElse(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM), List.of());
        return ticketService.findById(id, UUID.fromString(jwt.getSubject()), roles);
    }

    @Override
    @PutMapping("/{id}")
    public TicketResponse edit(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID id,
                               @RequestBody @Valid TicketUpdateRequest request) {
        return ticketService.edit(id, UUID.fromString(jwt.getSubject()), request);
    }
}
