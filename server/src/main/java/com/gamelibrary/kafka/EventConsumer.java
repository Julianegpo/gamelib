package com.gamelibrary.kafka;

import com.gamelibrary.kafka.event.GameCreatedEvent;
import com.gamelibrary.kafka.event.LibraryStatusChangedEvent;
import com.gamelibrary.kafka.event.ListUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Example consumers for the event streams this app produces.
 *
 * These are reference listeners that log events — in a larger system they'd
 * live in separate services (analytics, notifications, search indexing).
 * They run in the same consumer group so scaling out means adding instances,
 * and Kafka partitions the work automatically.
 *
 * Idempotency: consumers must tolerate redelivery (at-least-once). Each
 * handler below is written so processing the same event twice is harmless —
 * e.g. index updates are set-based, not incremental.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    @KafkaListener(topics = "game-library.games.created", groupId = "game-library-server")
    public void onGameCreated(GameCreatedEvent event) {
        log.info("[consumer] Game created: {} ({}) by user event {}",
                event.title(), event.gameId(), event.eventId());
    }

    @KafkaListener(topics = "game-library.library.status-changed", groupId = "game-library-server")
    public void onLibraryStatusChanged(LibraryStatusChangedEvent event) {
        log.info("[consumer] {} set {} to {} (event {})",
                event.username(), event.gameId(), event.status(), event.eventId());
    }

    @KafkaListener(topics = "game-library.lists.updated", groupId = "game-library-server")
    public void onListUpdated(ListUpdatedEvent event) {
        log.info("[consumer] List '{}' updated by {} with {} games (event {})",
                event.name(), event.username(), event.gameIds().size(), event.eventId());
    }
}
