package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Edição completa do formulário (PUT): todos os campos são reenviados. Status e solicitante não mudam por aqui.
 */
@Schema(description = "Novos dados do chamado")
public record TicketUpdateRequest(
    @Schema(example = "Impressora do financeiro não imprime")
    @NotBlank @Size(max = 120)
    String title,

    @Schema(example = "A impressora do 2º andar mostra erro de papel; já troquei a bandeja.")
    @NotBlank @Size(max = 5000)
    String description,

    @Schema(description = "Categoria (ids em GET /categories)")
    @NotNull
    UUID categoryId,

    @Schema(description = "Prioridade de 1 (Baixa) a 4 (Crítica)", example = "3")
    @NotNull
    Short priorityId
) {}
