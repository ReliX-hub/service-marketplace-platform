package com.relix.marketplace.storage.scheduler;

import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.repository.StoredFileRepository;
import com.relix.marketplace.storage.service.StoredFileCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanFileScheduler {

    private static final int BATCH_SIZE = 100;

    private final StoredFileRepository storedFileRepository;
    private final StoredFileCleanupService cleanupService;
    private final ImageProperties imageProperties;

    @Scheduled(fixedDelayString = "${images.orphan-cleanup-interval-ms:3600000}")
    public void removeExpiredOrphans() {
        Instant cutoff = Instant.now().minus(imageProperties.getOrphanRetention());
        List<Long> ids = storedFileRepository
                .findByOwnerIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
                        cutoff,
                        PageRequest.of(0, BATCH_SIZE))
                .stream()
                .map(StoredFile::getId)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        try {
            cleanupService.deleteNow(ids);
        } catch (RuntimeException failure) {
            log.warn("Orphan file cleanup will retry after a storage or database failure", failure);
        }
    }
}
