package com.relix.marketplace.review.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void ratingAndCommentHaveStableApiLimits() {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .rating(0)
                .comment("x".repeat(2001))
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("rating", "comment");
    }
}
