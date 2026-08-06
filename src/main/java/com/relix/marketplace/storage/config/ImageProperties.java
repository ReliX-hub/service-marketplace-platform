package com.relix.marketplace.storage.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "images")
public class ImageProperties {

    @Min(1)
    private long maxSourceBytes = 8L * 1024L * 1024L;

    @Min(1)
    @Max(8)
    private int maxPerTicket = 8;

    @Min(1)
    @Max(8)
    private int maxPerDeliverable = 8;

    @Min(1)
    private int thumbWidth = 400;

    @Min(1)
    private int largeWidth = 1400;

    @Min(1)
    private long maxSourcePixels = 40_000_000L;

    @Min(1)
    private int maxSourceDimension = 12_000;

    @Min(1)
    private int recentWorkLimit = 6;

    @Min(1)
    private int recentWorkPerTicket = 2;

    @NotNull
    private Duration orphanRetention = Duration.ofHours(24);

    private boolean requireDeliverableOnDeliver;

    @AssertTrue(message = "images.thumb-width must not exceed images.large-width")
    public boolean isVariantSizeOrderValid() {
        return thumbWidth <= largeWidth;
    }

    @AssertTrue(message = "images.recent-work-per-ticket must not exceed images.recent-work-limit")
    public boolean isRecentWorkLimitValid() {
        return recentWorkPerTicket <= recentWorkLimit;
    }

    @AssertTrue(message = "images.orphan-retention must be positive")
    public boolean isOrphanRetentionValid() {
        return orphanRetention != null && !orphanRetention.isZero() && !orphanRetention.isNegative();
    }
}
