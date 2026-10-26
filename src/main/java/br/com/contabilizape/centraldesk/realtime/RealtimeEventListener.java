package br.com.contabilizape.centraldesk.realtime;

import br.com.contabilizape.centraldesk.security.Roles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Leva os eventos de domínio para os streams SSE. Roda depois do commit: ninguém recebe
 * aviso de algo que acabou não sendo gravado, e uma falha no envio não desfaz a operação.
 */
@Component
public class RealtimeEventListener {

    public static final String COMMENT_CREATED = "comment.created";
    public static final String TICKET_CREATED = "ticket.created";
    public static final String SESSION_ENDED = "session.ended";

    private final SseEmitterRegistry registry;

    public RealtimeEventListener(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        registry.sendToUsers(event.recipientIds(), COMMENT_CREATED, event.comment());
    }

    /** Avisa as abas abertas da pessoa e fecha os streams; o token antigo não reabre a conexão. */
    @TransactionalEventListener
    public void onSessionsEnded(SessionsEndedEvent event) {
        Set<UUID> user = Set.of(event.userId());
        registry.sendToUsers(user, SESSION_ENDED, Map.of("reason", "SESSIONS_ENDED"));
        registry.closeUsers(user);
    }

    /** Fecha os streams: o navegador reconecta renovando a sessão e a conexão nova já traz as permissões novas. */
    @TransactionalEventListener
    public void onUserPermissionsChanged(UserPermissionsChangedEvent event) {
        registry.closeUsers(Set.of(event.userId()));
    }

    @TransactionalEventListener
    public void onTicketCreated(TicketCreatedEvent event) {
        registry.sendToRoles(Set.of(Roles.TICKET_HANDLER, Roles.ADMIN), TICKET_CREATED, event.ticket());
    }
}
