package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.FileStorage;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoredFileCleanupServiceTest {

    @Mock
    private StoredFileRepository repository;
    @Mock
    private FileStorage fileStorage;
    @InjectMocks
    private StoredFileCleanupService cleanupService;

    @Test
    void deletesPhysicalAndRegistryObjectsButProtectsAttachedFiles() {
        StoredFile orphan = StoredFile.builder()
                .storageKey("orphan.jpg")
                .ownerId(null)
                .build();
        orphan.setId(1L);
        StoredFile attached = StoredFile.builder()
                .storageKey("attached.jpg")
                .ownerId(42L)
                .build();
        attached.setId(2L);
        when(repository.findAllByIdForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(orphan, attached));

        cleanupService.deleteNow(List.of(1L, 2L));

        verify(fileStorage).delete("orphan.jpg");
        verify(repository).delete(orphan);
        verify(fileStorage, never()).delete("attached.jpg");
        verify(repository, never()).delete(attached);
        verify(repository).flush();
    }
}
