package com.relix.marketplace.worker.repository;

import com.relix.marketplace.worker.entity.Credential;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {

    @EntityGraph(attributePaths = {"worker", "worker.user", "reviewedBy", "documentFile"})
    Optional<Credential> findByWorker_IdAndType(Long workerId, Credential.Type type);

    @EntityGraph(attributePaths = {"worker", "worker.user", "reviewedBy", "documentFile"})
    Page<Credential> findByWorker_Id(Long workerId, Pageable pageable);

    @EntityGraph(attributePaths = {"worker", "worker.user", "reviewedBy", "documentFile"})
    Page<Credential> findByStatus(Credential.Status status, Pageable pageable);

    @EntityGraph(attributePaths = {"worker", "worker.user", "reviewedBy", "documentFile"})
    @Query("select credential from Credential credential where credential.id = :credentialId")
    Optional<Credential> findDetailedById(@Param("credentialId") Long credentialId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from Credential credential where credential.id = :credentialId")
    Optional<Credential> findByIdForUpdate(@Param("credentialId") Long credentialId);

    @Query("""
            SELECT CASE WHEN COUNT(credential) > 0 THEN true ELSE false END
            FROM Credential credential
            WHERE credential.worker.user.id = :userId
              AND credential.type = :type
              AND credential.status = :status
              AND (credential.expiresAt IS NULL OR credential.expiresAt >= :today)
            """)
    boolean existsCurrentVerifiedCredential(
            @Param("userId") Long userId,
            @Param("type") Credential.Type type,
            @Param("status") Credential.Status status,
            @Param("today") LocalDate today);
}
