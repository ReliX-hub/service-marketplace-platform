package com.relix.marketplace.ticket.dto;

import com.relix.marketplace.storage.dto.ImageVariants;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.ticket.entity.TicketImage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A normalized ticket photo and its display metadata")
public record TicketImageResponse(
        @Schema(example = "91", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ImageVariants image,
        @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int position,
        @Schema(example = "Leak beneath the shutoff valve", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String caption) {

    public static TicketImageResponse from(TicketImage ticketImage, FileUrlService fileUrlService) {
        return new TicketImageResponse(
                ticketImage.getId(),
                new ImageVariants(
                        fileUrlService.toExternalUrl(ticketImage.getThumb()),
                        fileUrlService.toExternalUrl(ticketImage.getLarge())),
                ticketImage.getPosition().intValue(),
                ticketImage.getCaption());
    }
}
