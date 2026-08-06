package com.relix.marketplace.ticket.repository;

import com.relix.marketplace.ticket.dto.TicketSearchCriteria;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> publicBoard(TicketSearchCriteria criteria) {
        return publicBoard(criteria, Instant.now());
    }

    public static Specification<Ticket> publicBoard(TicketSearchCriteria criteria, Instant now) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), TicketStatus.OPEN));
            predicates.add(builder.or(
                    builder.isNull(root.get("expiresAt")),
                    builder.greaterThan(root.<Instant>get("expiresAt"), now)));
            predicates.add(builder.or(
                    builder.isNull(root.get("serviceWindowEnd")),
                    builder.greaterThan(root.<Instant>get("serviceWindowEnd"), now)));

            if (criteria.getKind() != null) {
                predicates.add(builder.equal(root.get("kind"), criteria.getKind()));
            }
            if (criteria.getCategoryId() != null) {
                predicates.add(builder.equal(root.get("category").get("id"), criteria.getCategoryId()));
            }
            if (hasText(criteria.getQ())) {
                String pattern = "%" + escapeLike(criteria.getQ().trim().toLowerCase(Locale.ROOT)) + "%";
                Predicate titleMatches = builder.like(builder.lower(root.get("title")), pattern, '\\');
                Predicate descriptionMatches = builder.like(
                        builder.lower(root.get("description")), pattern, '\\');
                predicates.add(builder.or(titleMatches, descriptionMatches));
            }
            if (hasText(criteria.getCity())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("city")),
                        criteria.getCity().trim().toLowerCase(Locale.ROOT)));
            }
            if (criteria.getLocationMode() != null) {
                predicates.add(builder.equal(root.get("locationMode"), criteria.getLocationMode()));
            }
            if (criteria.getPricingMode() != null) {
                predicates.add(builder.equal(root.get("pricingMode"), criteria.getPricingMode()));
            }
            if (criteria.getMinPrice() != null || criteria.getMaxPrice() != null) {
                predicates.add(priceOverlaps(criteria.getMinPrice(), criteria.getMaxPrice(), root, builder));
            }
            if (criteria.getServiceFrom() != null) {
                predicates.add(builder.or(
                        builder.isNull(root.get("serviceWindowEnd")),
                        builder.greaterThan(
                                root.<Instant>get("serviceWindowEnd"),
                                criteria.getServiceFrom())));
            }
            if (criteria.getServiceTo() != null) {
                predicates.add(builder.or(
                        builder.isNull(root.get("serviceWindowStart")),
                        builder.lessThan(
                                root.<Instant>get("serviceWindowStart"),
                                criteria.getServiceTo())));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Ticket> ownedBy(Long authorId, TicketKind kind, TicketStatus status) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("author").get("id"), authorId));
            if (kind != null) {
                predicates.add(builder.equal(root.get("kind"), kind));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate priceOverlaps(
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Path<Ticket> root,
            CriteriaBuilder builder) {
        List<Predicate> fixedPredicates = new ArrayList<>();
        fixedPredicates.add(builder.equal(root.get("pricingMode"), PricingMode.FIXED));
        if (minPrice != null) {
            fixedPredicates.add(builder.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            fixedPredicates.add(builder.lessThanOrEqualTo(root.get("price"), maxPrice));
        }

        List<Predicate> rangePredicates = new ArrayList<>();
        rangePredicates.add(builder.equal(root.get("pricingMode"), PricingMode.BUDGET_RANGE));
        // Interval overlap: ticket.max >= requested.min and ticket.min <= requested.max.
        if (minPrice != null) {
            rangePredicates.add(builder.greaterThanOrEqualTo(root.get("budgetMax"), minPrice));
        }
        if (maxPrice != null) {
            rangePredicates.add(builder.lessThanOrEqualTo(root.get("budgetMin"), maxPrice));
        }

        return builder.or(
                builder.and(fixedPredicates.toArray(Predicate[]::new)),
                builder.and(rangePredicates.toArray(Predicate[]::new)));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
