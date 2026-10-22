package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.validation.CorporateEmail;
import br.com.contabilizape.centraldesk.validation.Cpf;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Correção dos dados de uma conta pelo administrador. Todos os campos vão juntos, mesmo os que não mudaram.")
public record AdminUserUpdateRequest(
    @Schema(description = "Nome completo", example = "Maria da Silva")
    @NotBlank @Size(max = 150)
    String fullName,

    @Schema(description = "CPF só com os 11 dígitos, sem pontuação; os dígitos verificadores são conferidos", example = "52998224725")
    @NotBlank
    @Cpf
    String cpf,

    @Schema(description = "E-mail institucional (@contabilizape.com.br). É o login da pessoa.", example = "maria.silva@contabilizape.com.br")
    @NotBlank @Email @CorporateEmail @Size(max = 255)
    String email,

    @Schema(description = "Departamento (ids em GET /departments)", example = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016")
    @NotNull
    UUID departmentId
) {}
