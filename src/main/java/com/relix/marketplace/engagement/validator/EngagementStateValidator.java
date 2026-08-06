package com.relix.marketplace.engagement.validator;

import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.engagement.entity.EngagementStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class EngagementStateValidator {

    private static final Map<EngagementStatus, Set<EngagementStatus>> TRANSITIONS = transitions();

    private EngagementStateValidator() {
    }

    public static boolean canTransition(EngagementStatus from, EngagementStatus to) {
        return from != null && to != null && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validate(EngagementStatus from, EngagementStatus to) {
        if (!canTransition(from, to)) {
            throw new ConflictException(
                    "Invalid engagement transition from " + from + " to " + to,
                    "INVALID_ENGAGEMENT_TRANSITION",
                    Map.of("from", String.valueOf(from), "to", String.valueOf(to)));
        }
    }

    public static void validateForOperation(
            EngagementStatus from,
            EngagementStatus to,
            String operation) {
        if (!canTransition(from, to)) {
            throw new ConflictException(
                    "Cannot " + operation + " an engagement in status " + from,
                    "INVALID_ENGAGEMENT_STATE",
                    Map.of(
                            "operation", operation,
                            "currentStatus", String.valueOf(from),
                            "requiredTransition", String.valueOf(to)));
        }
    }

    private static Map<EngagementStatus, Set<EngagementStatus>> transitions() {
        EnumMap<EngagementStatus, Set<EngagementStatus>> transitions =
                new EnumMap<>(EngagementStatus.class);
        transitions.put(
                EngagementStatus.ACCEPTED,
                EnumSet.of(EngagementStatus.FUNDED, EngagementStatus.CANCELLED));
        transitions.put(
                EngagementStatus.FUNDED,
                EnumSet.of(EngagementStatus.IN_PROGRESS, EngagementStatus.CANCELLED));
        transitions.put(
                EngagementStatus.IN_PROGRESS,
                EnumSet.of(EngagementStatus.DELIVERED, EngagementStatus.CANCELLED));
        transitions.put(
                EngagementStatus.DELIVERED,
                EnumSet.of(EngagementStatus.COMPLETED, EngagementStatus.DISPUTED));
        transitions.put(
                EngagementStatus.DISPUTED,
                EnumSet.of(EngagementStatus.COMPLETED, EngagementStatus.REFUNDED));
        transitions.put(EngagementStatus.COMPLETED, Set.of());
        transitions.put(EngagementStatus.CANCELLED, Set.of());
        transitions.put(EngagementStatus.REFUNDED, Set.of());
        return Map.copyOf(transitions);
    }
}
