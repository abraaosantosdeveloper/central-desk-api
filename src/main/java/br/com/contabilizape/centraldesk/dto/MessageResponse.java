package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta só com uma mensagem para mostrar à pessoa")
public record MessageResponse(String message) {}
