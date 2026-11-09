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
            summary = "Exportar chamados em planilha Excel (.xlsx)",
            description = """
                    Baixa uma planilha já formatada com um chamado por linha e **todos os campos do chamado**: \
                    número, título, descrição, status, categoria, prioridade, solicitante, setores de origem e de \
                    destino, responsável, datas (abertura, atribuição, primeiro atendimento, encerramento e última \
                    atualização), quem encerrou, resolução e ID. Campos sem valor ficam com a célula vazia. As datas \
                    estão no fuso da empresa. Não sai a conversa (comentários) nem CPF ou e-mail.

                    **Escopo (lido do banco):** administrador exporta todos os chamados; coordenador, os que o próprio \
                    setor abriu ou recebeu; os demais recebem 403.""",
            security = @SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH))
    @ApiResponse(
            responseCode = "200",
            description = "Arquivo `chamados-AAAA-MM-DD.xlsx`.",
            content = @Content(
                    mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "401", description = "Sem sessão: é preciso entrar (ou renovar a sessão).", content = @Content)
    @ApiResponse(
            responseCode = "403",
            description = "Só administradores e coordenadores de setor, ou a conta foi desativada.",
            content = @Content(mediaType = ProblemExamples.MEDIA_TYPE, schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<byte[]> exportXlsx(@Parameter(hidden = true) Jwt jwt);
}
