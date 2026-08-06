package com.relix.marketplace.engagement.repository;

import com.relix.marketplace.engagement.entity.EngagementDeliverable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EngagementDeliverableRepository extends JpaRepository<EngagementDeliverable, Long> {

    /**
     * Position compaction can temporarily collide with an existing position. The
     * database constraint is DEFERRABLE so the final, compact ordering can be
     * validated atomically at transaction commit.
     */
    @Modifying
    @Query(value = "SET CONSTRAINTS uk_engagement_deliverables_position DEFERRED", nativeQuery = true)
    void deferPositionConstraint();

    @EntityGraph(attributePaths = {"thumb", "large", "submittedBy"})
    List<EngagementDeliverable> findByEngagement_IdOrderByPositionAscIdAsc(Long engagementId);

    @EntityGraph(attributePaths = {"engagement", "thumb", "large", "submittedBy"})
    Optional<EngagementDeliverable> findByIdAndEngagement_Id(Long deliverableId, Long engagementId);

    long countByEngagement_Id(Long engagementId);
}
