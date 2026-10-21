package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Novo responsável pelo chamado (ids em GET /tickets/assignees)")
public record ReassignRequest(@NotNull UUID assigneeId) {}
