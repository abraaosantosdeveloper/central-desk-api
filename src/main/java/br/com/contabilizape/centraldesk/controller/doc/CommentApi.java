package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.CommentCreateRequest;
import br.com.contabilizape.centraldesk.dto.CommentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/**
 * Documentação OpenAPI dos comentários (implementada pelo CommentController).
 */
@Tag(name = "Comentários")
public interface CommentApi {

    @Operation(
            summary = "Comenta num chamado",
            description = """
                    Podem comentar as mesmas pessoas que veem o chamado: **quem abriu**, a Inovação \
                    (`TICKET_HANDLER`) e administradores. O texto é gravado **cifrado** (AES-256-GCM); \
                    a resposta já traz o texto aberto. Os participantes conectados em `GET /events/stream` \
                    recebem o evento `comment.created`.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "201", description = "Comentário criado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommentResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Texto em branco ou com mais de 5000 caracteres (lista em `errors`).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "A conta foi desativada depois do login.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Chamado inexistente, ou que o usuário não pode ver.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Chamado não encontrado", value = ProblemExamples.TICKET_NOT_FOUND)))
    @ApiResponse(
            responseCode = "409",
            description = "O chamado está encerrado e não recebe novos comentários.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Chamado encerrado", value = ProblemExamples.TICKET_CLOSED_FOR_COMMENTS)))
    ResponseEntity<CommentResponse> create(@Parameter(hidden = true) Jwt jwt,
                                           @Parameter(description = "Id do chamado") UUID ticketId,
                                           CommentCreateRequest request);

    @Operation(
            summary = "Conversa do chamado",
            description = """
                    Todos os comentários, do mais antigo para o mais novo, já decifrados. Também funciona \
                    com o chamado encerrado (só a criação é bloqueada).""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Comentários.",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = CommentResponse.class))))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "A conta foi desativada depois do login.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Chamado inexistente, ou que o usuário não pode ver.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    List<CommentResponse> list(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do chamado") UUID ticketId);
}
