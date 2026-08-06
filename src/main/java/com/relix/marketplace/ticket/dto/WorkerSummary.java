package com.relix.marketplace.ticket.dto;

import com.relix.marketplace.worker.entity.WorkerProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Worker summary present for worker-authored service offers")
public class WorkerSummary {

    @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "Alex Home Services", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;
    @Schema(example = "Reliable help for moves and home projects", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String headline;
    @Schema(example = "https://cdn.example.com/avatars/12.jpg", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String avatarUrl;
    @Schema(type = "string", example = "4.92", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal rating;
    @Schema(example = "38", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer reviewCount;
    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean verified;

    public static WorkerSummary from(WorkerProfile worker) {
        if (worker == null) {
            return null;
        }
        return WorkerSummary.builder()
                .id(worker.getId())
                .displayName(worker.getDisplayName())
                .headline(worker.getHeadline())
                .avatarUrl(worker.getUser().getAvatarUrl())
                .rating(worker.getRating())
                .reviewCount(worker.getReviewCount())
                .verified(worker.getVerified())
                .build();
    }
}
