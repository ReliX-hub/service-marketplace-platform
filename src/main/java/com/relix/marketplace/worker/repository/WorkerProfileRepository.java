package com.relix.marketplace.worker.repository;

import com.relix.marketplace.worker.entity.WorkerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkerProfileRepository extends JpaRepository<WorkerProfile, Long> {

    @Override
    @EntityGraph(attributePaths = "user")
    Page<WorkerProfile> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<WorkerProfile> findByVerifiedTrue(Pageable pageable);

    Optional<WorkerProfile> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select worker from WorkerProfile worker where worker.id = :id")
    Optional<WorkerProfile> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO worker_profiles (user_id, display_name)
            VALUES (:userId, :displayName)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int createDefaultIfMissing(@Param("userId") Long userId, @Param("displayName") String displayName);
}
