package com.relix.marketplace.ticket.dto;

import com.relix.marketplace.catalog.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ticket category summary embedded to avoid follow-up API calls")
public class CategorySummary {

    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "MOVING", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
    @Schema(example = "Moving Help", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(example = "truck", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String icon;

    public static CategorySummary from(Category category) {
        return CategorySummary.builder()
                .id(category.getId())
                .code(category.getCode())
                .name(category.getName())
                .icon(category.getIcon())
                .build();
    }
}
