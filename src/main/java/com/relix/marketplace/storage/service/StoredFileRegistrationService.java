package com.relix.marketplace.storage.service;

import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVariant;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.repository.StoredFileRepository;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoredFileRegistrationService {

    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<StoredFileReservation> reservePair(
            FileOwnerType ownerType,
            Long uploaderId,
            String thumbKey,
            EncodedImage thumb,
            String largeKey,
            EncodedImage large) {
        User uploader = userRepository.getReferenceById(uploaderId);
        StoredFile thumbFile = create(ownerType, uploader, FileVariant.THUMB, thumbKey, thumb);
        StoredFile largeFile = create(ownerType, uploader, FileVariant.LARGE, largeKey, large);
        StoredFile savedThumb = storedFileRepository.save(thumbFile);
        StoredFile savedLarge = storedFileRepository.save(largeFile);
        storedFileRepository.flush();
        return List.of(
                new StoredFileReservation(savedThumb.getId(), savedThumb.getStorageKey()),
                new StoredFileReservation(savedLarge.getId(), savedLarge.getStorageKey()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StoredFileReservation reserveLarge(
            FileOwnerType ownerType,
            Long uploaderId,
            String storageKey,
            EncodedImage image) {
        User uploader = userRepository.getReferenceById(uploaderId);
        StoredFile saved = storedFileRepository.saveAndFlush(
                create(ownerType, uploader, FileVariant.LARGE, storageKey, image));
        return new StoredFileReservation(saved.getId(), saved.getStorageKey());
    }

    private StoredFile create(
            FileOwnerType ownerType,
            User uploader,
            FileVariant variant,
            String key,
            EncodedImage image) {
        return StoredFile.builder()
                .storageKey(key)
                .variant(variant)
                .visibility(FileVisibility.PRIVATE)
                .ownerType(ownerType)
                .ownerId(null)
                .uploader(uploader)
                .contentType(image.contentType())
                .byteSize(image.byteSize())
                .width(image.width())
                .height(image.height())
                .build();
    }
}
