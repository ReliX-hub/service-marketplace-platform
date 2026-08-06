package com.relix.marketplace.ticket.dto;

import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.TicketKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
@Schema(description = "Data used to create a draft marketplace ticket")
public class TicketCreateRequest {

    @NotNull
    @Schema(description = "Listing direction", example = "REQUEST", requiredMode = Schema.RequiredMode.REQUIRED)
    private TicketKind kind;

    @NotNull
    @Schema(description = "Active marketplace category ID", example = "3",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;

    @NotBlank
    @Size(max = 200)
    @Schema(example = "Help moving a sofa this Saturday", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(example = "Two flights of stairs; I can help carry the smaller items.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;

    @NotNull
    @Schema(description = "Price constraint model", example = "BUDGET_RANGE",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private PricingMode pricingMode;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Schema(type = "string", example = "80.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal price;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Schema(type = "string", example = "70.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal budgetMin;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Schema(type = "string", example = "100.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal budgetMax;

    @Builder.Default
    @Pattern(regexp = "[A-Z]{3}")
    @Schema(example = "USD", defaultValue = "USD", pattern = "[A-Z]{3}",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String currency = "USD";

    @NotNull
    @Schema(description = "Where the work is performed", example = "ON_SITE",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private LocationMode locationMode;

    @Size(max = 500)
    @Schema(example = "1200 Market Street", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String address;

    @Size(max = 100)
    @Schema(example = "Chicago", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String city;

    @DecimalMin("-90.00000000")
    @DecimalMax("90.00000000")
    @Digits(integer = 2, fraction = 8)
    @Schema(type = "string", example = "41.87811360", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal latitude;

    @DecimalMin("-180.00000000")
    @DecimalMax("180.00000000")
    @Digits(integer = 3, fraction = 8)
    @Schema(type = "string", example = "-87.62979820", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal longitude;

    @Positive
    @Schema(example = "120", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer estimatedDurationMinutes;

    @Future
    @Schema(description = "Earliest acceptable service start", type = "string", format = "date-time",
            example = "2027-02-01T14:00:00Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant serviceWindowStart;

    @Future
    @Schema(description = "Latest acceptable service end", type = "string", format = "date-time",
            example = "2027-02-03T22:00:00Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant serviceWindowEnd;

    @Future
    @Schema(type = "string", format = "date-time", example = "2027-01-31T18:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant expiresAt;
}
