package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.FileStorage;
import com.relix.marketplace.storage.StoredObjectRequest;
import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.storage.entity.FileOwnerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagedImageIngestService {

    private final ImageIngestService imageIngestService;
    private final StoredFileRegistrationService registrationService;
    private final StoredFileCleanupService cleanupService;
    private final FileStorage fileStorage;
    private final ImageProperties properties;

    public StagedImageFiles stagePair(
            MultipartFile upload,
            FileOwnerType ownerType,
            Long uploaderId) {
        return stagePair(readUpload(upload), ownerType, uploaderId);
    }

    public StagedImageFiles stagePair(
            byte[] source,
            FileOwnerType ownerType,
            Long uploaderId) {
        ImageIngestResult normalized = imageIngestService.ingest(source);
        String thumbKey = keyFor(normalized.thumb());
        String largeKey = keyFor(normalized.large());
        List<StoredFileReservation> reservations = registrationService.reservePair(
                ownerType,
                uploaderId,
                thumbKey,
                normalized.thumb(),
                largeKey,
                normalized.large());
        try {
            fileStorage.store(new StoredObjectRequest(thumbKey, normalized.thumb().bytes()));
            fileStorage.store(new StoredObjectRequest(largeKey, normalized.large().bytes()));
            return new StagedImageFiles(reservations.get(0).id(), reservations.get(1).id());
        } catch (RuntimeException failure) {
            cleanupQuietly(reservations.stream().map(StoredFileReservation::id).toList());
            throw failure;
        }
    }

    public StagedFile stageLarge(
            MultipartFile upload,
            FileOwnerType ownerType,
            Long uploaderId) {
        ImageIngestResult normalized = imageIngestService.ingest(readUpload(upload));
        String key = keyFor(normalized.large());
        StoredFileReservation reservation = registrationService.reserveLarge(
                ownerType,
                uploaderId,
                key,
                normalized.large());
        try {
            fileStorage.store(new StoredObjectRequest(key, normalized.large().bytes()));
            return new StagedFile(reservation.id());
        } catch (RuntimeException failure) {
            cleanupQuietly(List.of(reservation.id()));
            throw failure;
        }
    }

    private byte[] readUpload(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new InvalidImageException("Image file is required", "IMAGE_EMPTY");
        }
        if (upload.getSize() > properties.getMaxSourceBytes()) {
            throw new InvalidImageException(
                    "Image exceeds the maximum upload size",
                    "IMAGE_TOO_LARGE",
                    Map.of("maxBytes", properties.getMaxSourceBytes()));
        }
        try {
            return upload.getBytes();
        } catch (IOException exception) {
            throw new InvalidImageException(
                    "Image upload could not be read",
                    "IMAGE_CORRUPT",
                    Map.of(),
                    exception);
        }
    }

    private String keyFor(EncodedImage image) {
        String extension = "image/png".equals(image.contentType()) ? ".png" : ".jpg";
        return UUID.randomUUID() + extension;
    }

    private void cleanupQuietly(List<Long> ids) {
        try {
            cleanupService.deleteNow(ids);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Staged file cleanup will be retried by the orphan scheduler: {}", ids, cleanupFailure);
        }
    }
}
