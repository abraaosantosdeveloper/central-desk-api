package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.CommentApi;
import br.com.contabilizape.centraldesk.dto.CommentCreateRequest;
import br.com.contabilizape.centraldesk.dto.CommentResponse;
import br.com.contabilizape.centraldesk.security.TokenService;
import br.com.contabilizape.centraldesk.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/tickets/{ticketId}/comments")
public class CommentController implements CommentApi {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Override
    @PostMapping
    public ResponseEntity<CommentResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId,
                                                  @RequestBody @Valid CommentCreateRequest request) {
        CommentResponse comment = commentService.create(ticketId, UUID.fromString(jwt.getSubject()), roles(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @Override
    @GetMapping
    public List<CommentResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId) {
        return commentService.list(ticketId, UUID.fromString(jwt.getSubject()), roles(jwt));
    }

    private static List<String> roles(Jwt jwt) {
        return Objects.requireNonNullElse(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM), List.of());
    }
}
