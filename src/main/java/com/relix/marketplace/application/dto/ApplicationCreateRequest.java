package com.relix.marketplace.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A response to an open marketplace ticket")
public class ApplicationCreateRequest {

    @NotNull(message = "Proposed amount is required")
    @DecimalMin(value = "0.00", message = "Proposed amount cannot be negative")
    @Schema(description = "Proposed engagement amount in the ticket currency", example = "150.00",
            type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal proposedAmount;

    @Size(max = 5000, message = "Message must be at most 5000 characters")
    @Schema(example = "I am available Saturday morning and have a moving van.", maxLength = 5000,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String message;

    @Future(message = "Proposed start must be in the future")
    @Schema(description = "Proposed start within the ticket service window", type = "string",
            format = "date-time", example = "2027-02-02T14:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant proposedStart;
    @Future(message = "Proposed end must be in the future")
    @Schema(description = "Proposed end within the ticket service window", type = "string",
            format = "date-time", example = "2027-02-02T17:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant proposedEnd;
}
