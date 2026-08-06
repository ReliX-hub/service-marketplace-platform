package com.relix.marketplace.application.dto;

import com.relix.marketplace.application.entity.ApplicationStatus;
import com.relix.marketplace.ticket.entity.TicketKind;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Marketplace application")
public class ApplicationResponse {

    @Schema(example = "91", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ticketId;
    @Schema(description = "Direction of the listing being answered", example = "REQUEST",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private TicketKind ticketKind;
    @Schema(example = "Help moving a sofa this Saturday", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ticketTitle;
    @Schema(example = "18", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long applicantId;
    @Schema(example = "Jordan Lee", requiredMode = Schema.RequiredMode.REQUIRED)
    private String applicantName;
    @Schema(example = "https://cdn.example.com/avatars/18.jpg", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String applicantAvatarUrl;
    @Schema(example = "150.00", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal proposedAmount;
    @Schema(example = "I am available Saturday morning and have a moving van.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String message;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T14:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant proposedStart;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T17:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant proposedEnd;
    @Schema(description = "Application lifecycle state", example = "PENDING",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private ApplicationStatus status;
    @Schema(description = "Created engagement after acceptance", example = "33",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long engagementId;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant updatedAt;
}
