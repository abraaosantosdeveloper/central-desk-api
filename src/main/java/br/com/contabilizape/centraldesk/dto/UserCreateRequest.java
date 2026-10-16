package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.validation.CorporateEmail;
import br.com.contabilizape.centraldesk.validation.Cpf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UserCreateRequest(
    @NotBlank @Size(max = 150)
    String fullName,

    @NotBlank
    @Cpf
    String cpf,

    @NotBlank @Email @CorporateEmail @Size(max = 255)
    String email,

    @NotBlank @Size(min = 8, max = 72)
    @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "deve conter ao menos uma letra e um número")
    String password,

    @NotNull 
    UUID departmentId
) {}
