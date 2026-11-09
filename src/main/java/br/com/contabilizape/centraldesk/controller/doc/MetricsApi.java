package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import br.com.contabilizape.centraldesk.dto.MetricsFilter;
import br.com.contabilizape.centraldesk.dto.MetricsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Documentação OpenAPI do painel de métricas (implementada pelo MetricsController).
 */
@Tag(name = "Painéis")
public interface MetricsApi {

    @Operation(
            summary = "Painel de métricas",
            description = """
                    Como foi o atendimento no período. Os chamados do período são os **abertos** nele; o período \
                    anterior tem a mesma duração e vem junto, para comparação. Sem `from` e `to`, valem os últimos \
                    30 dias (máximo de 366). Tempos em segundos.

                    **Escopo (lido do banco):** administrador vê todos os setores e pode filtrar por \
                    `originDepartmentId`; coordenador vê só o próprio setor, como origem (o filtro de setor é \
                    ignorado e `sectorLocked` vem `true`); os demais recebem 403. `now` (abertos agora) e \
                    `oldestUnassigned` não dependem do período.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(
            responseCode = "200",
            description = "Métricas do período.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MetricsResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Período invertido ou maior que 366 dias (`errors.from`), ou setor inexistente.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Sem sessão: é preciso entrar (ou renovar a sessão).", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Só administradores e coordenadores de setor, ou a conta foi desativada.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    MetricsResponse metrics(@Parameter(hidden = true) Jwt jwt, @ParameterObject MetricsFilter filter);
}
