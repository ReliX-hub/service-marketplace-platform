package com.relix.marketplace.engagement.validator;

import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngagementStateValidatorTest {

    private static final Map<EngagementStatus, Set<EngagementStatus>> ALLOWED = allowedTransitions();

    @Test
    void acceptsEveryConfiguredEscrowTransition() {
        ALLOWED.forEach((from, targets) -> targets.forEach(to -> {
            assertTrue(EngagementStateValidator.canTransition(from, to));
            assertDoesNotThrow(() -> EngagementStateValidator.validate(from, to));
        }));
    }

    @Test
    void rejectsEveryTransitionOutsideTheEscrowGraph() {
        for (EngagementStatus from : EngagementStatus.values()) {
            for (EngagementStatus to : EngagementStatus.values()) {
                if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
                    assertFalse(EngagementStateValidator.canTransition(from, to));
                    ConflictException exception = assertThrows(
                            ConflictException.class,
                            () -> EngagementStateValidator.validate(from, to));
                    assertEquals("INVALID_ENGAGEMENT_TRANSITION", exception.getCode());
                }
            }
        }
    }

    @Test
    void rejectsNullEndpoints() {
        assertFalse(EngagementStateValidator.canTransition(null, EngagementStatus.ACCEPTED));
        assertFalse(EngagementStateValidator.canTransition(EngagementStatus.ACCEPTED, null));
    }

    @Test
    void operationValidationUsesMachineReadableStateError() {
        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> EngagementStateValidator.validateForOperation(
                        EngagementStatus.ACCEPTED,
                        EngagementStatus.DELIVERED,
                        "deliver"));

        assertEquals("INVALID_ENGAGEMENT_STATE", exception.getCode());
    }

    @Test
    void refundOutcomeBelongsToDisputeResolutionNotCancellation() {
        assertTrue(EngagementStateValidator.canTransition(
                EngagementStatus.DISPUTED,
                EngagementStatus.REFUNDED));
        assertFalse(EngagementStateValidator.canTransition(
                EngagementStatus.CANCELLED,
                EngagementStatus.REFUNDED));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> EngagementStateValidator.validateForOperation(
                        EngagementStatus.CANCELLED,
                        EngagementStatus.REFUNDED,
                        "complete refund"));

        assertEquals("INVALID_ENGAGEMENT_STATE", exception.getCode());
    }

    private static Map<EngagementStatus, Set<EngagementStatus>> allowedTransitions() {
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
        return transitions;
    }
}
