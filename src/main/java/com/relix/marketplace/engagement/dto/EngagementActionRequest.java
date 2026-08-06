package com.relix.marketplace.engagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional reason accompanying an engagement lifecycle action")
public class EngagementActionRequest {

    @Size(max = 500, message = "Reason must be at most 500 characters")
    @Schema(example = "Schedule changed before work began.", maxLength = 500,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String reason;
}
