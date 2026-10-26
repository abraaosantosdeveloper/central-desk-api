package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tamanho da fila de atendimento")
public record QueueCountResponse(
    @Schema(description = "Chamados `OPEN`, ainda sem responsável. É o mesmo número de `GET /tickets/queue` "
            + "(`totalElements` sem filtros) e o que o servidor envia no evento `queue.changed`.", example = "7")
    long openCount
) {}
