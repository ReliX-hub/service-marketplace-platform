package com.relix.marketplace.ticket.dto;

import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.storage.dto.ImageVariants;
import com.relix.marketplace.storage.service.FileUrlService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full public or owner-facing ticket representation")
public class TicketDetailResponse {

    @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "REQUEST", requiredMode = Schema.RequiredMode.REQUIRED)
    private TicketKind kind;
    @Schema(example = "Help moving a sofa this Saturday", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    @Schema(example = "Two flights of stairs; I can help carry the smaller items.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;
    @Schema(example = "BUDGET_RANGE", requiredMode = Schema.RequiredMode.REQUIRED)
    private PricingMode pricingMode;
    @Schema(type = "string", example = "80.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal price;
    @Schema(type = "string", example = "70.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal budgetMin;
    @Schema(type = "string", example = "100.00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal budgetMax;
    @Schema(example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
    private String currency;
    @Schema(example = "ON_SITE", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocationMode locationMode;
    @Schema(example = "1200 Market Street", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String address;
    @Schema(example = "Chicago", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String city;
    @Schema(type = "string", example = "41.87811360", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal latitude;
    @Schema(type = "string", example = "-87.62979820", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal longitude;
    @Schema(example = "120", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer estimatedDurationMinutes;
    @Schema(example = "OPEN", requiredMode = Schema.RequiredMode.REQUIRED)
    private TicketStatus status;
    @Schema(description = "First managed image variants; absent when the ticket has no photos",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private ImageVariants coverImage;
    @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer imageCount;
    @Builder.Default
    @Schema(description = "Managed ticket photos in display order", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TicketImageResponse> images = List.of();
    @Schema(example = "128", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long viewCount;
    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer applicationCount;
    @Schema(description = "Earliest acceptable service start", type = "string", format = "date-time",
            example = "2027-02-01T14:00:00Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant serviceWindowStart;
    @Schema(description = "Latest acceptable service end", type = "string", format = "date-time",
            example = "2027-02-03T22:00:00Z", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant serviceWindowEnd;
    @Schema(type = "string", format = "date-time", example = "2027-01-31T18:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant expiresAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant updatedAt;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private AuthorSummary author;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private CategorySummary category;
    @Schema(description = "Present for OFFER tickets", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private WorkerSummary worker;

    public static TicketDetailResponse from(
            Ticket ticket,
            List<TicketImageResponse> images,
            FileUrlService fileUrlService) {
        return TicketDetailResponse.builder()
                .id(ticket.getId())
                .kind(ticket.getKind())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .pricingMode(ticket.getPricingMode())
                .price(ticket.getPrice())
                .budgetMin(ticket.getBudgetMin())
                .budgetMax(ticket.getBudgetMax())
                .currency(ticket.getCurrency())
                .locationMode(ticket.getLocationMode())
                .address(ticket.getAddress())
                .city(ticket.getCity())
                .latitude(ticket.getLatitude())
                .longitude(ticket.getLongitude())
                .estimatedDurationMinutes(ticket.getEstimatedDurationMinutes())
                .status(ticket.getStatus())
                .coverImage(coverImage(ticket, fileUrlService))
                .imageCount(ticket.getImageCount() == null ? 0 : ticket.getImageCount().intValue())
                .images(images == null ? List.of() : List.copyOf(images))
                .viewCount(ticket.getViewCount())
                .applicationCount(ticket.getApplicationCount())
                .serviceWindowStart(ticket.getServiceWindowStart())
                .serviceWindowEnd(ticket.getServiceWindowEnd())
                .expiresAt(ticket.getExpiresAt())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .author(AuthorSummary.from(ticket.getAuthor()))
                .category(CategorySummary.from(ticket.getCategory()))
                .worker(WorkerSummary.from(ticket.getWorker()))
                .build();
    }

    private static ImageVariants coverImage(Ticket ticket, FileUrlService fileUrlService) {
        if (ticket.getCoverImageUrl() == null) {
            return null;
        }
        String large = ticket.getCoverImageLargeUrl() == null
                ? ticket.getCoverImageUrl()
                : ticket.getCoverImageLargeUrl();
        return new ImageVariants(
                fileUrlService.toExternalUrl(ticket.getCoverImageUrl()),
                fileUrlService.toExternalUrl(large));
    }
}
