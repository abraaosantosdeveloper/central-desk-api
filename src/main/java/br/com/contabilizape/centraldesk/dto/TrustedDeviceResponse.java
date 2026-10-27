package br.com.contabilizape.centraldesk.dto;

import br.com.contabilizape.centraldesk.model.TrustedDevice;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Navegador dispensado do código por 30 dias")
public record TrustedDeviceResponse(
    UUID id,
    @Schema(description = "Resumo do navegador, para reconhecer na lista", example = "Chrome no Windows")
    String label,
    OffsetDateTime createdAt,
    @Schema(description = "Último acesso dispensado do código; vazio se ainda não foi usado")
    OffsetDateTime lastUsedAt,
    @Schema(description = "Depois disto, o login neste navegador volta a pedir o código")
    OffsetDateTime expiresAt
) {
    public static TrustedDeviceResponse from(TrustedDevice device) {
        return new TrustedDeviceResponse(device.getId(), device.getLabel(), device.getCreatedAt(),
                device.getLastUsedAt(), device.getExpiresAt());
    }
}
