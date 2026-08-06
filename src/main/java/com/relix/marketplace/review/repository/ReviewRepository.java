package com.relix.marketplace.review.repository;

import com.relix.marketplace.review.entity.Review;
import com.relix.marketplace.review.entity.ReviewDirection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByEngagement_IdAndDirection(Long engagementId, ReviewDirection direction);

    @EntityGraph(attributePaths = {"reviewer", "reviewee"})
    Page<Review> findByReviewee_IdAndDirection(
            Long revieweeId,
            ReviewDirection direction,
            Pageable pageable);

    @Query(value = """
            SELECT COALESCE(ROUND(CAST(AVG(review.rating) AS numeric), 2), 0.00) AS "averageRating",
                   COUNT(*) AS "reviewCount"
            FROM reviews review
            WHERE review.reviewee_id = :revieweeId
              AND review.direction = :direction
            """, nativeQuery = true)
    RatingAggregate aggregateForReviewee(
            @Param("revieweeId") Long revieweeId,
            @Param("direction") String direction);

    interface RatingAggregate {
        BigDecimal getAverageRating();

        Long getReviewCount();
    }
}
