package com.relix.marketplace.ticket.dto;

import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.TicketKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
@Schema(description = "Public ticket-board filters")
public class TicketSearchCriteria {

    @Schema(example = "REQUEST", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private TicketKind kind;
    @Schema(example = "3", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long categoryId;

    @Schema(description = "Case-insensitive text search over title and description", example = "moving sofa",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String q;

    @DecimalMin("0.00")
    @Schema(type = "string", example = "50.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal minPrice;

    @DecimalMin("0.00")
    @Schema(type = "string", example = "200.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal maxPrice;

    @Schema(example = "Chicago", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String city;
    @Schema(example = "ON_SITE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocationMode locationMode;
    @Schema(example = "BUDGET_RANGE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private PricingMode pricingMode;

    @Schema(description = "Requested interval start; matches open-ended or overlapping service windows",
            type = "string", format = "date-time", example = "2027-02-01T14:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant serviceFrom;

    @Schema(description = "Requested interval end; matches open-ended or overlapping service windows",
            type = "string", format = "date-time", example = "2027-02-03T22:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant serviceTo;

    @Builder.Default
    @Schema(example = "createdAt,desc", defaultValue = "createdAt,desc",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String sort = "createdAt,desc";

    @Builder.Default
    @Schema(example = "0", defaultValue = "0", minimum = "0",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer page = 0;

    @Builder.Default
    @Schema(example = "20", defaultValue = "20", minimum = "1", maximum = "100",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer size = 20;
}
