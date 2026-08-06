package com.relix.marketplace.storage.scheduler;

import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.repository.StoredFileRepository;
import com.relix.marketplace.storage.service.StoredFileCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanFileSchedulerTest {

    @Mock
    private StoredFileRepository repository;
    @Mock
    private StoredFileCleanupService cleanupService;
    @Mock
    private ImageProperties properties;

    private OrphanFileScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(properties.getOrphanRetention()).thenReturn(Duration.ofHours(24));
        scheduler = new OrphanFileScheduler(repository, cleanupService, properties);
    }

    @Test
    void deletesOnlyExpiredOwnerlessRowsSelectedByTheRepository() {
        StoredFile first = StoredFile.builder().build();
        first.setId(11L);
        StoredFile second = StoredFile.builder().build();
        second.setId(12L);
        when(repository.findByOwnerIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
                any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        scheduler.removeExpiredOrphans();

        verify(cleanupService).deleteNow(List.of(11L, 12L));
        verify(repository).findByOwnerIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
                any(Instant.class), eq(org.springframework.data.domain.PageRequest.of(0, 100)));
    }

    @Test
    void doesNothingWhenNoExpiredOrphanExists() {
        when(repository.findByOwnerIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
                any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        scheduler.removeExpiredOrphans();

        verify(cleanupService, never()).deleteNow(any());
    }

    @Test
    void leavesFailureForTheNextScheduledRetry() {
        StoredFile orphan = StoredFile.builder().build();
        orphan.setId(21L);
        when(repository.findByOwnerIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
                any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(orphan));
        org.mockito.Mockito.doThrow(new IllegalStateException("disk unavailable"))
                .when(cleanupService).deleteNow(List.of(21L));

        assertThatCode(scheduler::removeExpiredOrphans).doesNotThrowAnyException();
        verify(cleanupService).deleteNow(List.of(21L));
    }
}
