package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados de uma categoria de chamado")
public record CategoryRequest(
    @Schema(example = "Telefonia") @NotBlank @Size(max = 60)
    String label,
    @Schema(description = "Cor em hexadecimal (#RRGGBB)", example = "#0891B2")
    @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use uma cor no formato #RRGGBB.")
    String color
) {}
