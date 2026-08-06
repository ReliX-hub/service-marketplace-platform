package com.relix.marketplace.engagement.repository;

import com.relix.marketplace.engagement.entity.Engagement;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EngagementRepository extends JpaRepository<Engagement, Long> {

    Optional<Engagement> findByApplication_Id(Long applicationId);

    @EntityGraph(attributePaths = {"client", "worker", "worker.user", "ticket", "application"})
    Page<Engagement> findByClient_Id(Long clientId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "worker", "worker.user", "ticket", "application"})
    Page<Engagement> findByClient_IdAndStatus(
            Long clientId,
            com.relix.marketplace.engagement.entity.EngagementStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"client", "worker", "worker.user", "ticket", "application"})
    Page<Engagement> findByWorker_User_Id(Long workerUserId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "worker", "worker.user", "ticket", "application"})
    Page<Engagement> findByWorker_User_IdAndStatus(
            Long workerUserId,
            com.relix.marketplace.engagement.entity.EngagementStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"client", "worker", "worker.user", "ticket", "application"})
    @Query("select engagement from Engagement engagement where engagement.id = :id")
    Optional<Engagement> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "client", "worker", "worker.user", "ticket", "application"
    })
    @Query("select engagement from Engagement engagement where engagement.id = :id")
    Optional<Engagement> findByIdForUpdate(@Param("id") Long id);
}
