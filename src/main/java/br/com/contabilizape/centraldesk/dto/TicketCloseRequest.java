package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Encerramento do chamado: o que foi feito para resolver")
public record TicketCloseRequest(
    @Schema(description = "O que foi feito (até 2000 caracteres). Fica no histórico do chamado.",
            example = "Troquei a fonte do notebook e testei com o usuário.")
    @NotBlank(message = "Descreva o que foi feito.") @Size(max = 2000)
    String resolution
) {}
