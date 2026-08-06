package com.relix.marketplace.review.dto;

import com.relix.marketplace.review.entity.Review;
import com.relix.marketplace.review.entity.ReviewDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Published engagement review")
public class ReviewResponse {

    @Schema(example = "105", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "33", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long engagementId;
    @Schema(description = "Which participant reviewed the other", example = "CLIENT_TO_WORKER",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private ReviewDirection direction;
    @Schema(minimum = "1", maximum = "5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rating;
    @Schema(example = "Clear communication and excellent work.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String comment;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private ReviewParticipantResponse reviewer;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private ReviewParticipantResponse reviewee;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T18:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;

    public static ReviewResponse from(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .engagementId(review.getEngagement().getId())
                .direction(review.getDirection())
                .rating(review.getRating())
                .comment(review.getComment())
                .reviewer(ReviewParticipantResponse.from(review.getReviewer()))
                .reviewee(ReviewParticipantResponse.from(review.getReviewee()))
                .createdAt(review.getCreatedAt())
                .build();
    }
}
