package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Novo comentário no chamado")
public record CommentCreateRequest(
    @Schema(description = "Texto do comentário (até 5000 caracteres). É gravado cifrado.", example = "Pode mandar uma foto do erro?")
    @NotBlank @Size(max = 5000)
    String content
) {}
