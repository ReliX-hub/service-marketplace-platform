package com.relix.marketplace.review.service;

import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.review.dto.ReviewCreateRequest;
import com.relix.marketplace.review.dto.ReviewResponse;
import com.relix.marketplace.review.entity.Review;
import com.relix.marketplace.review.entity.ReviewDirection;
import com.relix.marketplace.review.repository.ReviewRepository;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.user.repository.UserRepository;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.WorkerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final EngagementRepository engagementRepository;
    private final UserRepository userRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Transactional
    public ReviewResponse createReview(Long engagementId, ReviewCreateRequest request) {
        Engagement engagement = engagementRepository.findByIdForUpdate(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("Engagement", engagementId));
        User reviewer = currentUserService.getCurrentUser();

        if (engagement.getStatus() != EngagementStatus.COMPLETED) {
            throw new BusinessException(
                    "Reviews can only be submitted after an engagement is completed",
                    "REVIEW_ONLY_AFTER_COMPLETION");
        }

        ReviewContext context = resolveContext(engagement, reviewer);
        if (reviewRepository.existsByEngagement_IdAndDirection(engagementId, context.direction())) {
            throw duplicateReview(engagementId, context.direction());
        }

        AggregateTarget aggregateTarget = lockAggregateTarget(context, engagement);

        Review review = Review.builder()
                .engagement(engagement)
                .reviewer(reviewer)
                .reviewee(context.reviewee())
                .direction(context.direction())
                .rating(request.getRating())
                .comment(normalizeComment(request.getComment()))
                .build();

        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateReview(engagementId, context.direction());
        }

        refreshAggregate(context, aggregateTarget);
        auditService.log(
                "REVIEW",
                review.getId(),
                "REVIEW_CREATED",
                context.direction() == ReviewDirection.CLIENT_TO_WORKER ? "CLIENT" : "WORKER",
                reviewer.getId(),
                Map.of(
                        "engagementId", engagementId,
                        "direction", context.direction().name(),
                        "rating", request.getRating()));

        return ReviewResponse.from(review);
    }

    public PageResponse<ReviewResponse> getWorkerReviews(Long workerId, Pageable pageable) {
        WorkerProfile worker = workerProfileRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker profile", workerId));
        Page<Review> reviews = reviewRepository.findByReviewee_IdAndDirection(
                worker.getUser().getId(),
                ReviewDirection.CLIENT_TO_WORKER,
                pageable);
        return PageResponse.of(reviews, ReviewResponse::from);
    }

    public PageResponse<ReviewResponse> getUserReviews(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        Page<Review> reviews = reviewRepository.findByReviewee_IdAndDirection(
                userId,
                ReviewDirection.WORKER_TO_CLIENT,
                pageable);
        return PageResponse.of(reviews, ReviewResponse::from);
    }

    private ReviewContext resolveContext(Engagement engagement, User reviewer) {
        Long reviewerId = reviewer.getId();
        User client = engagement.getClient();
        User workerUser = engagement.getWorker().getUser();

        if (client.getId().equals(reviewerId)) {
            return new ReviewContext(ReviewDirection.CLIENT_TO_WORKER, workerUser);
        }
        if (workerUser.getId().equals(reviewerId)) {
            return new ReviewContext(ReviewDirection.WORKER_TO_CLIENT, client);
        }
        throw new ForbiddenException(
                "Only engagement participants may submit a review",
                "NOT_ENGAGEMENT_PARTICIPANT");
    }

    private AggregateTarget lockAggregateTarget(ReviewContext context, Engagement engagement) {
        if (context.direction() == ReviewDirection.CLIENT_TO_WORKER) {
            WorkerProfile worker = workerProfileRepository.findByIdForUpdate(engagement.getWorker().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Worker profile",
                            engagement.getWorker().getId()));
            return new AggregateTarget(worker, null);
        }

        User client = userRepository.findByIdForUpdate(engagement.getClient().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User",
                        engagement.getClient().getId()));
        return new AggregateTarget(null, client);
    }

    private void refreshAggregate(ReviewContext context, AggregateTarget aggregateTarget) {
        ReviewRepository.RatingAggregate aggregate = reviewRepository.aggregateForReviewee(
                context.reviewee().getId(),
                context.direction().name());
        BigDecimal average = aggregate.getAverageRating();
        int count = Math.toIntExact(aggregate.getReviewCount());

        if (context.direction() == ReviewDirection.CLIENT_TO_WORKER) {
            WorkerProfile worker = aggregateTarget.worker();
            worker.setRating(average);
            worker.setReviewCount(count);
            workerProfileRepository.save(worker);
            return;
        }

        User client = aggregateTarget.client();
        client.setClientRating(average);
        client.setClientReviewCount(count);
        userRepository.save(client);
    }

    private ConflictException duplicateReview(Long engagementId, ReviewDirection direction) {
        return new ConflictException(
                "This participant has already reviewed the engagement",
                "REVIEW_ALREADY_SUBMITTED",
                Map.of("engagementId", engagementId, "direction", direction.name()));
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    private record ReviewContext(ReviewDirection direction, User reviewee) {
    }

    private record AggregateTarget(WorkerProfile worker, User client) {
    }
}
