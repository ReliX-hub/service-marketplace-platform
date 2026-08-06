package com.relix.marketplace.ticket.dto;

import com.relix.marketplace.catalog.entity.Category;
import com.relix.marketplace.storage.config.StorageProperties;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketResponseTest {

    private final FileUrlService fileUrlService = new FileUrlService(new StorageProperties());

    @Test
    void legacySingleCoverUrlPopulatesBothVariantSlots() {
        Ticket ticket = ticket();
        ticket.setCoverImageUrl("https://legacy.example.com/ticket.jpg");
        ticket.setCoverImageLargeUrl(null);

        TicketSummaryResponse summary = TicketSummaryResponse.from(ticket, fileUrlService);
        TicketDetailResponse detail = TicketDetailResponse.from(ticket, List.of(), fileUrlService);

        assertThat(summary.getCoverImage().thumb()).isEqualTo("https://legacy.example.com/ticket.jpg");
        assertThat(summary.getCoverImage().large()).isEqualTo("https://legacy.example.com/ticket.jpg");
        assertThat(detail.getCoverImage()).isEqualTo(summary.getCoverImage());
    }

    private Ticket ticket() {
        User author = User.builder()
                .email("legacy-cover@example.com")
                .passwordHash("hash")
                .name("Legacy Cover Author")
                .build();
        author.setId(10L);
        Category category = Category.builder()
                .code("MOVING")
                .name("Moving")
                .active(true)
                .build();
        category.setId(20L);
        Ticket ticket = Ticket.builder()
                .kind(TicketKind.REQUEST)
                .author(author)
                .category(category)
                .title("Legacy cover ticket")
                .pricingMode(PricingMode.FIXED)
                .price(new BigDecimal("80.00"))
                .currency("USD")
                .locationMode(LocationMode.ON_SITE)
                .status(TicketStatus.OPEN)
                .build();
        ticket.setId(42L);
        return ticket;
    }
}
