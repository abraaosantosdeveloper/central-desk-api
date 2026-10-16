package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Formulário de abertura. Solicitante e status não vêm daqui: o solicitante é quem está logado
 * e todo chamado nasce OPEN.
 */
@Schema(description = "Dados para abrir um chamado")
public record TicketCreateRequest(
    @Schema(example = "Impressora do financeiro não imprime")
    @NotBlank @Size(max = 120)
    String title,

    @Schema(example = "Desde hoje cedo a impressora do 2º andar mostra erro de papel, mas a bandeja está cheia.")
    @NotBlank @Size(max = 5000)
    String description,

    @Schema(description = "Categoria (ids em GET /categories)")
    @NotNull
    UUID categoryId,

    @Schema(description = "Prioridade de 1 (Baixa) a 4 (Crítica); lista em GET /priorities", example = "2")
    @NotNull
    Short priorityId
) {}
