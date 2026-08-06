package com.relix.marketplace.settlement.repository;

import com.relix.marketplace.settlement.entity.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByEngagementId(Long engagementId);

    boolean existsByEngagementId(Long engagementId);

    List<Settlement> findByStatus(Settlement.SettlementStatus status);

    @EntityGraph(attributePaths = {"engagement"})
    @Query("SELECT s FROM Settlement s WHERE s.engagement.worker.id = :workerId")
    Page<Settlement> findByWorkerId(@Param("workerId") Long workerId, Pageable pageable);

    @EntityGraph(attributePaths = {"engagement"})
    Page<Settlement> findAllBy(Pageable pageable);

    @Query("SELECT s FROM Settlement s WHERE s.engagement.worker.id = :workerId AND s.status = :status ORDER BY s.createdAt DESC")
    List<Settlement> findByWorkerIdAndStatus(@Param("workerId") Long workerId, @Param("status") Settlement.SettlementStatus status);

    @Query("SELECT COALESCE(SUM(s.workerPayout), 0) FROM Settlement s WHERE s.engagement.worker.id = :workerId AND s.status = :status")
    java.math.BigDecimal sumWorkerPayoutByWorkerIdAndStatus(@Param("workerId") Long workerId, @Param("status") Settlement.SettlementStatus status);

    @Query("SELECT COUNT(s) FROM Settlement s WHERE s.engagement.worker.id = :workerId AND s.status = :status")
    long countByWorkerIdAndStatus(@Param("workerId") Long workerId, @Param("status") Settlement.SettlementStatus status);

    List<Settlement> findByBatchId(String batchId);

    @Query("SELECT COALESCE(SUM(s.workerPayout), 0) FROM Settlement s WHERE s.status = :status")
    java.math.BigDecimal sumWorkerPayoutByStatus(@Param("status") Settlement.SettlementStatus status);

    @Query("SELECT COUNT(s) FROM Settlement s WHERE s.status = :status")
    long countByStatus(@Param("status") Settlement.SettlementStatus status);
}
