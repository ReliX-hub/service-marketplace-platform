package com.relix.marketplace.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Stable zero-based pagination envelope used by every list endpoint")
public class PageResponse<T> {

    @Builder.Default
    @Schema(description = "Items on the requested page", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<T> items = List.of();

    @Schema(description = "Zero-based page index", example = "0", minimum = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int page;

    @Schema(description = "Requested page size", example = "20", minimum = "1", maximum = "100",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int size;

    @Schema(description = "Total matching item count", example = "42", minimum = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private long totalElements;

    @Schema(description = "Total page count", example = "3", minimum = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private int totalPages;

    @Schema(description = "Whether another page follows", example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean hasNext;

    public static <T, R> PageResponse<R> of(
            Page<T> source,
            Function<T, R> mapper) {
        Objects.requireNonNull(source, "source page must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");

        List<R> items = source.getContent().stream()
                .map(mapper)
                .toList();

        return PageResponse.<R>builder()
                .items(items)
                .page(source.getNumber())
                .size(source.getSize())
                .totalElements(source.getTotalElements())
                .totalPages(source.getTotalPages())
                .hasNext(source.hasNext())
                .build();
    }
}
