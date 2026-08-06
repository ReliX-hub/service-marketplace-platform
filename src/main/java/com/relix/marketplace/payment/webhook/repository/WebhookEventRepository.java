package com.relix.marketplace.payment.webhook.repository;

import com.relix.marketplace.payment.webhook.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO webhook_events (provider, event_id, event_type, payload)
            VALUES (:provider, :eventId, :eventType, CAST(:payload AS jsonb))
            ON CONFLICT (provider, event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("provider") String provider,
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("payload") String payload);

    Optional<WebhookEvent> findByProviderAndEventId(String provider, String eventId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update WebhookEvent event
               set event.processedAt = :processedAt
             where event.provider = :provider
               and event.eventId = :eventId
               and event.processedAt is null
            """)
    int markProcessed(
            @Param("provider") String provider,
            @Param("eventId") String eventId,
            @Param("processedAt") Instant processedAt);
}
