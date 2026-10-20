package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Edição do próprio perfil. Só o nome é editável; CPF, e-mail, departamento e permissões não mudam aqui.")
public record UserProfileUpdateRequest(
    @Schema(description = "Nome completo", example = "Maria da Silva Souza")
    @NotBlank @Size(max = 150)
    String fullName
) {}
