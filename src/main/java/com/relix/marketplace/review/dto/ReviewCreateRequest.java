package com.relix.marketplace.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Review submitted by an engagement participant; direction is derived server-side")
public class ReviewCreateRequest {

    @NotNull
    @Min(1)
    @Max(5)
    @Schema(description = "Whole-number rating from 1 to 5", example = "5", minimum = "1", maximum = "5",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer rating;

    @Size(max = 2000)
    @Schema(description = "Optional review text", maxLength = 2000,
            example = "Clear communication and excellent work.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String comment;
}
