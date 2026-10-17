package com.gamelibrary.kafka.event;

import com.gamelibrary.domain.GameStatus;

import java.time.Instant;

/**
 * Domain events published to Kafka. These are the contract between this
 * backend and any future consumers (analytics, notifications, other services).
 */
public sealed interface GameEvent permits GameCreatedEvent, LibraryStatusChangedEvent, ListUpdatedEvent {

    String eventId();

    Instant occurredAt();
}
