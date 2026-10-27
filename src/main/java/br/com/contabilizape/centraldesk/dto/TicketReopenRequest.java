package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Reabertura de um chamado encerrado")
public record TicketReopenRequest(
    @Schema(description = "Por que o chamado está sendo reaberto (10 a 1.000 caracteres); entra na conversa",
            example = "A impressora voltou a falhar depois de uma hora.")
    @NotBlank @Size(min = 10, max = 1000)
    String reason
) {}
