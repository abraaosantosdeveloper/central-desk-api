package br.com.contabilizape.centraldesk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Página de resultados com formato estável. Evita serializar o PageImpl do Spring Data,
 * cujo JSON não é garantido entre versões.
 */
@Schema(description = "Página de resultados")
public record PageResponse<T>(
    List<T> content,
    @Schema(description = "Número da página, começando em 0") int page,
    @Schema(description = "Itens por página") int size,
    long totalElements,
    int totalPages
) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
