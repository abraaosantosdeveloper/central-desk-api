package br.com.contabilizape.centraldesk.controller.doc;

import br.com.contabilizape.centraldesk.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Documentação OpenAPI da exportação de chamados (implementada pelo TicketExportController).
 */
@Tag(name = "Chamados")
public interface TicketExportApi {

    @Operation(
            summary = "Exportar chamados em CSV",
            description = """
                    Baixa os chamados num arquivo CSV (UTF-8 com BOM, separador `;`), para análise fora do sistema. \
                    Colunas: número, título, descrição, solicitante, setor de origem, setor de destino, responsável \
                    ("Em espera" quando o chamado está na fila), status e as datas de abertura, primeiro atendimento, \
                    encerramento e última atualização (fuso da empresa). Não sai CPF, e-mail, comentário nem resolução.

                    **Escopo (lido do banco):** administrador exporta todos os chamados; coordenador, os que o próprio \
                    setor abriu ou recebeu; os demais recebem 403.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(
            responseCode = "200",
            description = "Arquivo `chamados-AAAA-MM-DD.csv`.",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "Sem sessão: é preciso entrar (ou renovar a sessão).", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Só administradores e coordenadores de setor, ou a conta foi desativada.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<byte[]> exportCsv(@Parameter(hidden = true) Jwt jwt);
}
