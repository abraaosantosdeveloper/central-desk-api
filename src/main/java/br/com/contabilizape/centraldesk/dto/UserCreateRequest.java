package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.validation.Cpf;
import br.com.contabilizape.centraldesk.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Dados do formulário de cadastro")
public record UserCreateRequest(
    @Schema(description = "Nome completo", example = "Maria da Silva")
    @NotBlank @Size(max = 150)
    String fullName,

    @Schema(description = "CPF só com os 11 dígitos, sem pontuação; os dígitos verificadores são conferidos", example = "52998224725")
    @NotBlank
    @Cpf
    String cpf,

    @Schema(description = "E-mail (qualquer domínio)", example = "maria.silva@empresa.com.br")
    @NotBlank @Email @Size(max = 255)
    String email,

    @Schema(description = "Senha de 8 a 72 caracteres, com ao menos uma letra e um número",
            example = "senhaForte123", accessMode = Schema.AccessMode.WRITE_ONLY)
    @StrongPassword
    String password,

    @Schema(description = "Departamento escolhido (ids em GET /departments)", example = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016")
    @NotNull 
    UUID departmentId
) {}
