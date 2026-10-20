package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.TicketCreateRequest;
import br.com.contabilizape.centraldesk.dto.TicketFilter;
import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.dto.TicketSummaryResponse;
import br.com.contabilizape.centraldesk.dto.TicketUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Documentação OpenAPI dos endpoints de chamados (implementada pelo TicketController).
 */
@Tag(name = "Chamados")
public interface TicketApi {

    @Operation(
            summary = "Abre um chamado",
            description = """
                    Qualquer usuário logado e ativo pode abrir. O solicitante é sempre quem está logado, \
                    e o chamado nasce `OPEN`, sem responsável.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(
            responseCode = "201",
            description = "Chamado aberto.",
            headers = @Header(name = "Location", description = "Endereço do chamado: /tickets/{id}",
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TicketResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Campos inválidos (lista em `errors`), categoria ou prioridade inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "Campos inválidos", value = ProblemExamples.TICKET_INVALID_FIELDS),
                            @ExampleObject(name = "Categoria inexistente", value = ProblemExamples.CATEGORY_NOT_FOUND)
                    }))
    @ApiResponse(responseCode = "401", description = "Sem sessão: é preciso entrar (ou renovar a sessão).", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "A conta foi desativada.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<TicketResponse> open(@Parameter(hidden = true) Jwt jwt, TicketCreateRequest request);

    @Operation(
            summary = "Meus chamados",
            description = """
                    Chamados abertos pelo usuário logado, **mais recentes primeiro** (a ordenação enviada é ignorada). \
                    Paginado (`page` a partir de 0, `size` padrão 20, máximo 100). Filtros opcionais: `status`, \
                    `categoryId`, `priorityId`. Cada item é um resumo, **sem a descrição**.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Página de chamados.")
    @ApiResponse(
            responseCode = "400",
            description = "Filtro inválido (ex.: `status` desconhecido ou `categoryId` que não é UUID).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "A conta foi desativada depois do login.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    PageResponse<TicketSummaryResponse> listMine(@Parameter(hidden = true) Jwt jwt,
                                                 @ParameterObject TicketFilter filter,
                                                 @ParameterObject Pageable pageable);

    @Operation(
            summary = "Fila da Inovação",
            description = """
                    Chamados `OPEN` ainda sem responsável, de todos os solicitantes, **mais recentes primeiro**. \
                    Só para a Inovação (`TICKET_HANDLER`) e administradores. Mesmos filtros e paginação de \
                    `GET /tickets/mine`.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Página de chamados na fila.")
    @ApiResponse(
            responseCode = "400",
            description = "Filtro inválido.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é da Inovação nem administrador.", content = @Content)
    PageResponse<TicketSummaryResponse> listQueue(@Parameter(hidden = true) Jwt jwt,
                                                  @ParameterObject TicketFilter filter,
                                                  @ParameterObject Pageable pageable);

    @Operation(
            summary = "Chamados sob minha responsabilidade",
            description = """
                    Chamados em que o usuário logado é o responsável (em atendimento ou já encerrados), \
                    **mais recentes primeiro**. Só para a Inovação (`TICKET_HANDLER`) e administradores. \
                    Mesmos filtros e paginação de `GET /tickets/mine`.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Página de chamados.")
    @ApiResponse(
            responseCode = "400",
            description = "Filtro inválido.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é da Inovação nem administrador.", content = @Content)
    PageResponse<TicketSummaryResponse> listAssigned(@Parameter(hidden = true) Jwt jwt,
                                                     @ParameterObject TicketFilter filter,
                                                     @ParameterObject Pageable pageable);

    @Operation(
            summary = "Assume um chamado",
            description = """
                    O usuário logado passa a ser o responsável e o chamado vai de `OPEN` para `IN_PROGRESS`. \
                    Só para a Inovação (`TICKET_HANDLER`) e administradores. Se duas pessoas tentarem ao mesmo \
                    tempo, só a primeira consegue; a outra recebe 409. Sem corpo.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Chamado assumido.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TicketResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Quem chamou não é da Inovação nem administrador (sem corpo), ou a conta foi desativada.",
            content = @Content)
    @ApiResponse(
            responseCode = "404",
            description = "Chamado inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Chamado não encontrado", value = ProblemExamples.TICKET_NOT_FOUND)))
    @ApiResponse(
            responseCode = "409",
            description = "O chamado não está `OPEN` (já assumido ou encerrado).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Já assumido", value = ProblemExamples.INVALID_TICKET_TRANSITION)))
    TicketResponse assign(@Parameter(hidden = true) Jwt jwt, UUID id);

    @Operation(
            summary = "Detalhes de um chamado",
            description = """
                    Chamado completo, com descrição, responsável e encerramento. Veem: **quem abriu**, \
                    a Inovação (`TICKET_HANDLER`) e os administradores. Para os demais a resposta é 404, \
                    para não revelar que o chamado existe.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Chamado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TicketResponse.class)))
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
    TicketResponse findById(@Parameter(hidden = true) Jwt jwt, UUID id);

    @Operation(
            summary = "Edita um chamado",
            description = """
                    Troca título, descrição, categoria e prioridade (todos reenviados). Só **quem abriu** \
                    pode editar, e só enquanto o chamado está `OPEN`: depois que alguém da Inovação \
                    assume, o conteúdo fica congelado. Status e solicitante não mudam por aqui.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Chamado editado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TicketResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Campos inválidos (lista em `errors`), categoria ou prioridade inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Quem está logado não é o solicitante (ou a conta foi desativada).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Não é o solicitante", value = ProblemExamples.TICKET_EDIT_FORBIDDEN)))
    @ApiResponse(
            responseCode = "404",
            description = "Chamado inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "409",
            description = "O chamado já foi assumido.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Já assumido", value = ProblemExamples.TICKET_NOT_EDITABLE)))
    TicketResponse edit(@Parameter(hidden = true) Jwt jwt, UUID id, TicketUpdateRequest request);
}
