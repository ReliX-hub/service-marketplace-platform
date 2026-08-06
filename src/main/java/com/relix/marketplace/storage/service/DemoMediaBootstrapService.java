package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.FileStorage;
import com.relix.marketplace.storage.StoredObjectRequest;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketImage;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.repository.TicketImageRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class DemoMediaBootstrapService {

    private static final List<DemoImage> DEMO_IMAGES = List.of(
            new DemoImage("Weekly Home Refresh", "home-cleaning.jpg", "seed-home-cleaning"),
            new DemoImage("Emergency Plumbing Visit", "plumbing-leak.jpg", "seed-plumbing-leak"),
            new DemoImage("Small Apartment Moving Help", "moving-help.jpg", "seed-moving-help"),
            new DemoImage("Home Wi-Fi Optimization", "tech-support.jpg", "seed-tech-support"));

    private final EntityManager entityManager;
    private final ImageIngestService imageIngestService;
    private final StoredFileRegistrationService registrationService;
    private final StoredFileOwnershipService ownershipService;
    private final DemoMediaKeyResetService keyResetService;
    private final TicketImageRepository ticketImageRepository;
    private final FileStorage fileStorage;
    private final FileUrlService fileUrlService;

    @Transactional
    public int installMissingImages() {
        int installed = 0;
        for (DemoImage demoImage : DEMO_IMAGES) {
            Ticket ticket = findTicket(demoImage.ticketTitle());
            if (ticket.getImageCount() != null && ticket.getImageCount() > 0) {
                continue;
            }
            install(ticket, demoImage);
            installed++;
        }
        return installed;
    }

    private void install(Ticket ticket, DemoImage demoImage) {
        ImageIngestResult normalized = imageIngestService.ingest(read(demoImage.resourceName()));
        String thumbKey = keyFor(demoImage.keyPrefix(), "thumb", normalized.thumb());
        String largeKey = keyFor(demoImage.keyPrefix(), "large", normalized.large());
        // A repeatable seed may rebuild the registry while the Docker volume
        // survives. Replacing the same content-addressed key is safe: its bytes
        // are identical, so immutable public caching remains truthful.
        keyResetService.resetUnattachedKeys(List.of(thumbKey, largeKey));

        List<StoredFileReservation> reservations = registrationService.reservePair(
                FileOwnerType.TICKET_IMAGE,
                ticket.getAuthor().getId(),
                thumbKey,
                normalized.thumb(),
                largeKey,
                normalized.large());
        try {
            fileStorage.store(new StoredObjectRequest(thumbKey, normalized.thumb().bytes()));
            fileStorage.store(new StoredObjectRequest(largeKey, normalized.large().bytes()));
        } catch (RuntimeException failure) {
            fileStorage.delete(thumbKey);
            fileStorage.delete(largeKey);
            throw failure;
        }

        FileVisibility visibility = ticket.getStatus() == TicketStatus.DRAFT
                ? FileVisibility.PRIVATE
                : FileVisibility.PUBLIC;
        Map<Long, StoredFile> files = ownershipService.attach(
                reservations.stream().map(StoredFileReservation::id).toList(),
                FileOwnerType.TICKET_IMAGE,
                ticket.getId(),
                visibility);
        StoredFile thumb = files.get(reservations.get(0).id());
        StoredFile large = files.get(reservations.get(1).id());

        ticketImageRepository.save(TicketImage.builder()
                .ticket(ticket)
                .thumb(thumb)
                .large(large)
                .caption(null)
                .position((short) 0)
                .build());
        ticket.setImageCount((short) 1);
        ticket.setCoverImageUrl(fileUrlService.pathForKey(thumbKey));
        ticket.setCoverImageLargeUrl(fileUrlService.pathForKey(largeKey));
    }

    private Ticket findTicket(String title) {
        return entityManager.createQuery(
                        "select ticket from Ticket ticket join fetch ticket.author where ticket.title = :title",
                        Ticket.class)
                .setParameter("title", title)
                .getSingleResult();
    }

    private byte[] read(String resourceName) {
        try {
            return new ClassPathResource("db/seed/media/" + resourceName).getContentAsByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Development media resource is missing: " + resourceName, exception);
        }
    }

    private String extension(EncodedImage image) {
        return "image/png".equals(image.contentType()) ? ".png" : ".jpg";
    }

    private String keyFor(String prefix, String variant, EncodedImage image) {
        try {
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(image.bytes()));
            return prefix + "-" + variant + "-" + digest.substring(0, 24) + extension(image);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record DemoImage(String ticketTitle, String resourceName, String keyPrefix) {
    }
}
