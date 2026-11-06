package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.dto.TicketReopenRequest;
import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.AssigneeResponse;
import br.com.contabilizape.centraldesk.dto.OverviewAssigneeResponse;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

import java.util.List;
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
            summary = "Todos os chamados",
            description = """
                    **Administrador:** todos os chamados, de todos os setores, com filtros opcionais de \
                    `originDepartmentId` e `targetDepartmentId`. **Coordenador:** os chamados que o próprio setor \
                    abriu (origem) ou recebeu (destino); `relation` escolhe `ORIGIN`, `TARGET` ou `BOTH` (padrão), e \
                    os filtros de origem e destino são ignorados (o setor vem do banco, o cliente não amplia o escopo). \
                    Demais perfis: 403. Sem `status`, vêm abertos, em andamento e encerrados juntos. Filtros: \
                    `categoryId`, `priorityId`, `q` (título, número ou nome de quem abriu), `from`/`to` (dia de \
                    abertura, inclusive) e `orderBy` (`RECENT`, `PRIORITY`, `UPDATED`). Paginado (`size` padrão 20, \
                    máximo 100). `statusCounts` traz o total de cada aba com os mesmos filtros, menos o de status.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Página de chamados com os contadores por status.")
    @ApiResponse(
            responseCode = "400",
            description = "Filtro inválido (ex.: `status`, `relation` ou data em formato desconhecido).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Quem chamou não é administrador nem coordenador, ou a conta foi desativada.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    TicketOverviewResponse overview(@Parameter(hidden = true) Jwt jwt,
                                    @ParameterObject TicketOverviewFilter filter,
                                    @ParameterObject Pageable pageable);

    @Operation(
            summary = "Responsáveis para o filtro de \"Todos os chamados\"",
            description = """
                    Quem é responsável por algum chamado do recorte de quem pergunta: o **administrador** recebe \
                    todos; o **coordenador**, só quem atende chamados que o próprio setor abriu ou recebeu. Ordem \
                    alfabética. Use o `id` em `assigneeId` de `GET /tickets/overview`; para listar só os chamados \
                    sem responsável, use `unassigned=true`. Demais perfis: 403.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Responsáveis, em ordem alfabética.")
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Quem chamou não é administrador nem coordenador, ou a conta foi desativada.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    List<OverviewAssigneeResponse> overviewAssignees(@Parameter(hidden = true) Jwt jwt);

    @Operation(
            summary = "Tamanho da fila de atendimento",
            description = """
                    Quantos chamados `OPEN` (sem responsável) esperam na fila. Só para a Inovação \
                    (`TICKET_HANDLER`) e administradores. É o número do círculo do menu: a tela busca este valor \
                    ao conectar e a cada reconexão do stream, e depois o substitui pelo `openCount` de cada \
                    evento `queue.changed` (`GET /events/stream`).""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Tamanho atual da fila.")
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é da Inovação nem administrador.", content = @Content)
    QueueCountResponse countQueue(@Parameter(hidden = true) Jwt jwt);

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
            description = "Quem chamou não é da Inovação nem administrador (sem corpo), a conta foi desativada, "
                    + "ou quem chamou é o próprio solicitante: quem abriu o chamado não pode assumi-lo.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Próprio solicitante", value = ProblemExamples.SELF_ASSIGNMENT)))
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
            summary = "Lista quem pode atender chamados",
            description = """
                    Só Inovação e administradores. Contas ativas da Inovação e administradores, por nome, \
                    com a quantidade de chamados em atendimento de cada um. Usada na tela de reatribuir.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Pessoas que podem atender.",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AssigneeResponse.class))))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é da Inovação nem administrador.", content = @Content)
    List<AssigneeResponse> assignees();

    @Operation(
            summary = "Reatribui o chamado",
            description = """
                    O responsável atual passa o chamado a um colega, ou um administrador redistribui. Chamado na \
                    fila passa a `IN_PROGRESS` com o novo responsável; em atendimento, só o responsável muda e o \
                    `assignedAt` original é mantido. O destino precisa ser conta ativa da Inovação ou admin, e \
                    não pode ser quem abriu o chamado.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Chamado reatribuído.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TicketResponse.class)))
    @ApiResponse(responseCode = "400", description = "`assigneeId` ausente ou pessoa que não pode atender este chamado.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou não é o responsável nem administrador.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Chamado inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Chamado encerrado ou a pessoa já é a responsável.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    TicketResponse reassign(@Parameter(hidden = true) Jwt jwt, UUID id, ReassignRequest request);

    @Operation(
            summary = "Encerra um chamado",
            description = """
                    Leva o chamado de `OPEN` ou `IN_PROGRESS` para `CLOSED` e registra quem encerrou, quando e \
                    **o que foi feito** (`resolution`, obrigatório, até 2000 caracteres). Podem encerrar: a Inovação \
                    (`TICKET_HANDLER`) e administradores. **Quem abriu não encerra** o próprio chamado (403). Para os \
                    demais a resposta é 404, como em `GET /tickets/{id}`. A avaliação do atendimento é feita depois, \
                    em `POST /tickets/{id}/rating`.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "400", description = "`resolution` vazio ou com mais de 2000 caracteres.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "200", description = "Chamado encerrado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TicketResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Quem chamou abriu o chamado, ou a conta foi desativada depois do login.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Chamado inexistente, ou que o usuário não pode ver.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Chamado não encontrado", value = ProblemExamples.TICKET_NOT_FOUND)))
    @ApiResponse(
            responseCode = "409",
            description = "O chamado já está encerrado.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE,
                    schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "Já encerrado", value = ProblemExamples.TICKET_ALREADY_CLOSED)))
    TicketResponse close(@Parameter(hidden = true) Jwt jwt, UUID id, TicketCloseRequest request);

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

    @Operation(
            summary = "Reabre um chamado encerrado",
            description = """
                    O solicitante ou um administrador, até 7 dias depois do encerramento e no máximo 3 vezes por \
                    chamado. O motivo é obrigatório e entra na conversa. O chamado volta para o mesmo responsável \
                    (`IN_PROGRESS`) se ele ainda pode atender; senão, para a fila (`OPEN`, evento `queue.changed`). \
                    O encerramento anterior fica no histórico.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(responseCode = "200", description = "Chamado reaberto.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TicketResponse.class)))
    @ApiResponse(responseCode = "400", description = "Motivo ausente, curto (menos de 10) ou longo demais (mais de 1.000).",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão.", content = @Content)
    @ApiResponse(responseCode = "403", description = "Quem chamou vê o chamado, mas não é o solicitante nem administrador.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Chamado inexistente ou que a pessoa não pode ver.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Chamado não encerrado, fora do prazo de 7 dias ou já reaberto 3 vezes.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    TicketResponse reopen(@Parameter(hidden = true) Jwt jwt, @Parameter(description = "Id do chamado") UUID id,
                          TicketReopenRequest request);
}
