package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.RealtimeApi;
import br.com.contabilizape.centraldesk.realtime.SseEmitterRegistry;
import br.com.contabilizape.centraldesk.security.TokenService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/events")
public class RealtimeController implements RealtimeApi {

    private final SseEmitterRegistry registry;

    public RealtimeController(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @Override
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = Objects.requireNonNullElse(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM), List.of());
        return registry.subscribe(UUID.fromString(jwt.getSubject()), roles);
    }
}
