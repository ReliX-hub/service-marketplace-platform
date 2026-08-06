package com.relix.marketplace.storage.repository;

import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.StoredFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByStorageKey(String storageKey);

    @EntityGraph(attributePaths = "uploader")
    @Query("select file from StoredFile file where file.storageKey = :storageKey")
    Optional<StoredFile> findDetailedByStorageKey(@Param("storageKey") String storageKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select file from StoredFile file where file.id in :ids")
    List<StoredFile> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    List<StoredFile> findByOwnerTypeAndOwnerIdOrderByVariant(
            FileOwnerType ownerType,
            Long ownerId);

    List<StoredFile> findByOwnerIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
            Instant cutoff,
            Pageable pageable);
}
