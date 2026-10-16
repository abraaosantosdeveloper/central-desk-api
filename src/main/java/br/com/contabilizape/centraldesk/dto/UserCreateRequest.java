package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.validation.CorporateEmail;
import br.com.contabilizape.centraldesk.validation.Cpf;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Schema(description = "E-mail institucional (@contabilizape.com.br)", example = "maria.silva@contabilizape.com.br")
    @NotBlank @Email @CorporateEmail @Size(max = 255)
    String email,

    @Schema(description = "Senha de 8 a 72 caracteres, com ao menos uma letra e um número",
            example = "senhaForte123", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank @Size(min = 8, max = 72)
    @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "deve conter ao menos uma letra e um número")
    String password,

    @Schema(description = "Departamento escolhido (ids em GET /departments)", example = "3094ed3f-42ab-4bc0-9d8d-f6eb08891016")
    @NotNull 
    UUID departmentId
) {}
