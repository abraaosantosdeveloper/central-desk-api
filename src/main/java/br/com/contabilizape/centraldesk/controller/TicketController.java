package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.dto.TicketReopenRequest;
import br.com.contabilizape.centraldesk.service.TicketReopenService;
import br.com.contabilizape.centraldesk.controller.doc.TicketApi;
import br.com.contabilizape.centraldesk.dto.AssigneeResponse;
import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.QueueCountResponse;
import br.com.contabilizape.centraldesk.dto.ReassignRequest;
import br.com.contabilizape.centraldesk.dto.TicketCloseRequest;
import br.com.contabilizape.centraldesk.dto.TicketCreateRequest;
import br.com.contabilizape.centraldesk.dto.TicketFilter;
import br.com.contabilizape.centraldesk.dto.TicketOverviewFilter;
import br.com.contabilizape.centraldesk.dto.TicketOverviewResponse;
import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.dto.TicketSummaryResponse;
import br.com.contabilizape.centraldesk.dto.TicketUpdateRequest;
import br.com.contabilizape.centraldesk.security.TokenService;
import br.com.contabilizape.centraldesk.service.TicketOverviewService;
import br.com.contabilizape.centraldesk.service.TicketReassignmentService;
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
    private final TicketReassignmentService reassignmentService;
    private final TicketReopenService ticketReopenService;
    private final TicketOverviewService overviewService;

    public TicketController(TicketService ticketService, TicketReassignmentService reassignmentService,
                            TicketReopenService ticketReopenService, TicketOverviewService overviewService) {
        this.reassignmentService = reassignmentService;
        this.overviewService = overviewService;
        this.ticketReopenService = ticketReopenService;
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

    /** Administradores (tudo) e coordenadores (o próprio setor); os demais, 403 (regra no serviço, lida do banco). */
    @Override
    @GetMapping("/overview")
    public TicketOverviewResponse overview(@AuthenticationPrincipal Jwt jwt, TicketOverviewFilter filter,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return overviewService.overview(UUID.fromString(jwt.getSubject()), filter, pageable);
    }

    /** Só Inovação e administradores (regra no SecurityConfig). */
    @Override
    @GetMapping("/queue/count")
    public QueueCountResponse countQueue(@AuthenticationPrincipal Jwt jwt) {
        return ticketService.countQueue(UUID.fromString(jwt.getSubject()));
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

    /** Só Inovação e administradores (regra no SecurityConfig). */
    @Override
    @GetMapping("/assignees")
    public List<AssigneeResponse> assignees() {
        return reassignmentService.assignees();
    }

    /** Rota de Inovação e admins; dentro dela, só o responsável atual ou um admin (regra no serviço). */
    @Override
    @PatchMapping("/{id}/reassign")
    public TicketResponse reassign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @RequestBody @Valid ReassignRequest request) {
        List<String> roles = Objects.requireNonNullElse(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM), List.of());
        return reassignmentService.reassign(id, UUID.fromString(jwt.getSubject()), roles, request.assigneeId());
    }

    /** Inovação e administradores, nunca o solicitante (regra no TicketService). Corpo: o que foi feito (obrigatório). */
    @Override
    @PatchMapping("/{id}/close")
    public TicketResponse close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @RequestBody @Valid TicketCloseRequest request) {
        List<String> roles = Objects.requireNonNullElse(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM), List.of());
        return ticketService.close(id, UUID.fromString(jwt.getSubject()), roles, request.resolution());
    }

    /** Solicitante e administradores, até 7 dias depois do encerramento (SCRUM-208). */
    @Override
    @PatchMapping("/{id}/reopen")
    public TicketResponse reopen(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @RequestBody @Valid TicketReopenRequest request) {
        List<String> roles = Objects.requireNonNullElse(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM), List.of());
        return ticketReopenService.reopen(id, UUID.fromString(jwt.getSubject()), roles, request.reason());
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
