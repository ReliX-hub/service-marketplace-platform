package com.relix.marketplace.storage.service;

import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StoredFileOwnershipService {

    private final StoredFileRepository storedFileRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, StoredFile> attach(
            List<Long> fileIds,
            FileOwnerType expectedOwnerType,
            Long ownerId,
            FileVisibility visibility) {
        if (ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException("ownerId must be positive");
        }
        List<StoredFile> files = storedFileRepository.findAllByIdForUpdate(fileIds);
        if (files.size() != fileIds.size()) {
            throw new ResourceNotFoundException(
                    "One or more staged files were not found",
                    "STORED_FILE_NOT_FOUND",
                    Map.of("fileIds", fileIds));
        }

        Map<Long, StoredFile> byId = new HashMap<>();
        for (StoredFile file : files) {
            if (file.getOwnerType() != expectedOwnerType || file.getOwnerId() != null) {
                throw new ConflictException(
                        "A staged file cannot be attached to this resource",
                        "FILE_NOT_STAGED");
            }
            file.setOwnerId(ownerId);
            file.setVisibility(visibility);
            byId.put(file.getId(), file);
        }
        return Map.copyOf(byId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void detach(List<StoredFile> files) {
        for (StoredFile file : files) {
            file.setOwnerId(null);
            file.setVisibility(FileVisibility.PRIVATE);
        }
    }
}
