package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.FileStorage;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Profile("dev")
@RequiredArgsConstructor
class DemoMediaKeyResetService {

    private final StoredFileRepository storedFileRepository;
    private final FileStorage fileStorage;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetUnattachedKeys(List<String> storageKeys) {
        for (String storageKey : storageKeys) {
            storedFileRepository.findByStorageKey(storageKey).ifPresent(file -> {
                if (file.isAttached()) {
                    throw new IllegalStateException(
                            "Refusing to replace an attached development media object: " + storageKey);
                }
                delete(file);
            });
            fileStorage.delete(storageKey);
        }
        storedFileRepository.flush();
    }

    private void delete(StoredFile file) {
        fileStorage.delete(file.getStorageKey());
        storedFileRepository.delete(file);
    }
}
