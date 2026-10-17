package com.gamelibrary.kafka.event;

import com.gamelibrary.domain.GameStatus;

import java.time.Instant;

/**
 * A user set (or changed) the status of a game in their library.
 * Partitioned by username so all events for one user land on the same partition.
 */
public record LibraryStatusChangedEvent(
        String eventId,
        Instant occurredAt,
        String username,
        String gameId,
        GameStatus status
) implements GameEvent {
}
