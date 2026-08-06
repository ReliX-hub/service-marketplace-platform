package com.relix.marketplace.settlement.repository;

import com.relix.marketplace.settlement.entity.SettlementBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, Long> {

    Optional<SettlementBatch> findByBatchId(String batchId);

    boolean existsByBatchId(String batchId);

    Page<SettlementBatch> findAllBy(Pageable pageable);
}
