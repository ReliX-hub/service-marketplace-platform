package com.relix.marketplace.review.service;

import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private EngagementRepository engagementRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkerProfileRepository workerProfileRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void rejectsReviewBeforeEngagementCompletion() {
        Fixture fixture = fixture(EngagementStatus.DELIVERED);
        when(engagementRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(fixture.engagement()));
        when(currentUserService.getCurrentUser()).thenReturn(fixture.client());

        assertThatThrownBy(() -> reviewService.createReview(100L, request(5, "Great")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("REVIEW_ONLY_AFTER_COMPLETION");

        verify(reviewRepository, never()).saveAndFlush(any());
        verify(workerProfileRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectsNonParticipant() {
        Fixture fixture = fixture(EngagementStatus.COMPLETED);
        User outsider = user(3L, "Outsider");
        when(engagementRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(fixture.engagement()));
        when(currentUserService.getCurrentUser()).thenReturn(outsider);

        assertThatThrownBy(() -> reviewService.createReview(100L, request(5, "Great")))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("NOT_ENGAGEMENT_PARTICIPANT");

        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateReviewInTheDerivedDirection() {
        Fixture fixture = fixture(EngagementStatus.COMPLETED);
        when(engagementRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(fixture.engagement()));
        when(currentUserService.getCurrentUser()).thenReturn(fixture.client());
        when(reviewRepository.existsByEngagement_IdAndDirection(
                100L,
                ReviewDirection.CLIENT_TO_WORKER)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(100L, request(5, "Great")))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("REVIEW_ALREADY_SUBMITTED");

        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void clientReviewDerivesDirectionAndRebuildsWorkerAggregateUnderLock() {
        Fixture fixture = fixture(EngagementStatus.COMPLETED);
        ReviewRepository.RatingAggregate aggregate = aggregate(new BigDecimal("4.50"), 2L);
        stubCreate(fixture, fixture.client());
        when(workerProfileRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(fixture.worker()));
        when(reviewRepository.aggregateForReviewee(2L, "CLIENT_TO_WORKER"))
                .thenReturn(aggregate);

        ReviewResponse response = reviewService.createReview(
                100L,
                request(5, "  Excellent work.  "));

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(reviewCaptor.capture());
        Review saved = reviewCaptor.getValue();
        assertThat(saved.getDirection()).isEqualTo(ReviewDirection.CLIENT_TO_WORKER);
        assertThat(saved.getReviewer()).isSameAs(fixture.client());
        assertThat(saved.getReviewee()).isSameAs(fixture.workerUser());
        assertThat(saved.getComment()).isEqualTo("Excellent work.");
        assertThat(response.getDirection()).isEqualTo(ReviewDirection.CLIENT_TO_WORKER);
        assertThat(fixture.worker().getRating()).isEqualByComparingTo("4.50");
        assertThat(fixture.worker().getReviewCount()).isEqualTo(2);
        verify(workerProfileRepository).save(fixture.worker());
        verify(auditService).log(
                org.mockito.ArgumentMatchers.eq("REVIEW"),
                org.mockito.ArgumentMatchers.eq(900L),
                org.mockito.ArgumentMatchers.eq("REVIEW_CREATED"),
                org.mockito.ArgumentMatchers.eq("CLIENT"),
                org.mockito.ArgumentMatchers.eq(1L),
                any());
    }

    @Test
    void workerReviewDerivesDirectionAndRebuildsClientAggregateUnderLock() {
        Fixture fixture = fixture(EngagementStatus.COMPLETED);
        ReviewRepository.RatingAggregate aggregate = aggregate(new BigDecimal("3.75"), 4L);
        stubCreate(fixture, fixture.workerUser());
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fixture.client()));
        when(reviewRepository.aggregateForReviewee(1L, "WORKER_TO_CLIENT"))
                .thenReturn(aggregate);

        ReviewResponse response = reviewService.createReview(100L, request(4, "Good client"));

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(reviewCaptor.capture());
        Review saved = reviewCaptor.getValue();
        assertThat(saved.getDirection()).isEqualTo(ReviewDirection.WORKER_TO_CLIENT);
        assertThat(saved.getReviewer()).isSameAs(fixture.workerUser());
        assertThat(saved.getReviewee()).isSameAs(fixture.client());
        assertThat(response.getDirection()).isEqualTo(ReviewDirection.WORKER_TO_CLIENT);
        assertThat(fixture.client().getClientRating()).isEqualByComparingTo("3.75");
        assertThat(fixture.client().getClientReviewCount()).isEqualTo(4);
        verify(userRepository).save(fixture.client());
    }

    @Test
    void publicWorkerReviewsUseTheWorkerUserAndReturnPageEnvelope() {
        Fixture fixture = fixture(EngagementStatus.COMPLETED);
        Review review = review(
                901L,
                fixture,
                fixture.client(),
                fixture.workerUser(),
                ReviewDirection.CLIENT_TO_WORKER);
        PageRequest pageable = PageRequest.of(0, 20);
        when(workerProfileRepository.findById(10L)).thenReturn(Optional.of(fixture.worker()));
        when(reviewRepository.findByReviewee_IdAndDirection(
                2L,
                ReviewDirection.CLIENT_TO_WORKER,
                pageable)).thenReturn(new PageImpl<>(List.of(review), pageable, 1));

        PageResponse<ReviewResponse> response = reviewService.getWorkerReviews(10L, pageable);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getItems().get(0).getDirection())
                .isEqualTo(ReviewDirection.CLIENT_TO_WORKER);
    }

    private void stubCreate(Fixture fixture, User currentUser) {
        when(engagementRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(fixture.engagement()));
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(900L);
            return review;
        });
    }

    private ReviewRepository.RatingAggregate aggregate(BigDecimal average, long count) {
        ReviewRepository.RatingAggregate aggregate =
                org.mockito.Mockito.mock(ReviewRepository.RatingAggregate.class);
        when(aggregate.getAverageRating()).thenReturn(average);
        when(aggregate.getReviewCount()).thenReturn(count);
        return aggregate;
    }

    private ReviewCreateRequest request(int rating, String comment) {
        return ReviewCreateRequest.builder()
                .rating(rating)
                .comment(comment)
                .build();
    }

    private Fixture fixture(EngagementStatus status) {
        User client = user(1L, "Client");
        User workerUser = user(2L, "Worker");
        WorkerProfile worker = WorkerProfile.builder()
                .user(workerUser)
                .displayName("Worker Profile")
                .build();
        worker.setId(10L);
        Engagement engagement = Engagement.builder()
                .client(client)
                .worker(worker)
                .status(status)
                .amount(new BigDecimal("50.00"))
                .build();
        engagement.setId(100L);
        return new Fixture(client, workerUser, worker, engagement);
    }

    private User user(Long id, String name) {
        User user = User.builder()
                .email(name.toLowerCase() + "@example.com")
                .passwordHash("hash")
                .name(name)
                .role(User.UserRole.USER)
                .status(User.UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return user;
    }

    private Review review(
            Long id,
            Fixture fixture,
            User reviewer,
            User reviewee,
            ReviewDirection direction) {
        Review review = Review.builder()
                .engagement(fixture.engagement())
                .reviewer(reviewer)
                .reviewee(reviewee)
                .direction(direction)
                .rating(5)
                .comment("Excellent")
                .build();
        review.setId(id);
        return review;
    }

    private record Fixture(
            User client,
            User workerUser,
            WorkerProfile worker,
            Engagement engagement) {
    }
}
