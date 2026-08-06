package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.FileStorage;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoredFileCleanupService {

    private final StoredFileRepository storedFileRepository;
    private final FileStorage fileStorage;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteNow(List<Long> ids) {
        List<StoredFile> files = storedFileRepository.findAllByIdForUpdate(ids);
        for (StoredFile file : files) {
            if (file.getOwnerId() != null) {
                continue;
            }
            fileStorage.delete(file.getStorageKey());
            storedFileRepository.delete(file);
        }
        storedFileRepository.flush();
    }
}
