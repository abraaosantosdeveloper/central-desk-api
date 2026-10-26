package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
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

                    Além dos eventos, o servidor escreve um comentário (`:ping`) a cada 25 segundos para a \
                    conexão não ficar ociosa e ser derrubada por um proxy. O navegador ignora esse comentário.

                    As permissões do usuário são lidas do token **ao abrir** a conexão. Quando elas mudam \
                    (departamento trocado, perfil de administrador concedido ou removido, pedido de entrada na \
                    Inovação aprovado), o servidor **fecha** o stream do usuário sem enviar evento: a reconexão \
                    automática renova a sessão e a conexão nova já nasce com as permissões certas.

                    A resposta leva `X-Accel-Buffering: no` para o nginx não segurar os eventos em buffer.

                    Limite conhecido: funciona com uma única instância da aplicação.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Conexão aberta; os eventos chegam enquanto ela durar.",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(type = "string", example = EVENT_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    ResponseEntity<SseEmitter> stream(@Parameter(hidden = true) Jwt jwt);
}
