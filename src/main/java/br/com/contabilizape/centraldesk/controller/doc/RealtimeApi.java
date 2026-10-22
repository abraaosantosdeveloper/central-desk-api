package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Documentação OpenAPI do stream de eventos em tempo real (implementada pelo RealtimeController).
 */
@Tag(name = "Tempo real")
public interface RealtimeApi {

    String EVENT_EXAMPLE = """
            event:comment.created
            data:{"id":"...","ticketId":"...","authorName":"Carlos","content":"Chego em 5 minutos"}

            """;

    @Operation(
            summary = "Stream de eventos do usuário (SSE)",
            description = """
                    Uma conexão `text/event-stream` por usuário (cada aba abre a sua), autenticada pelo cookie \
                    `access_token`. No navegador: `new EventSource("/events/stream", { withCredentials: true })`; \
                    a reconexão é automática.

                    Eventos (`event:` + `data:` em JSON):
                    - `connected`: enviado ao abrir a conexão;
                    - `comment.created`: comentário novo num chamado em que o usuário é solicitante, responsável \
                    ou autor (`data` = comentário);
                    - `ticket.created`: chamado aberto; só para a Inovação e administradores (`data` = resumo, \
                    sem a descrição);
                    - `session.ended`: as sessões do usuário foram encerradas (senha trocada, e-mail de login \
                    trocado pelo admin). A conexão é fechada logo depois; a tela deve conferir a sessão \
                    (`GET /users/me`), que responde 401 nos dispositivos que perderam o acesso.

                    Limite conhecido: funciona com uma única instância da aplicação.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Conexão aberta; os eventos chegam enquanto ela durar.",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(type = "string", example = EVENT_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    SseEmitter stream(@Parameter(hidden = true) Jwt jwt);
}
