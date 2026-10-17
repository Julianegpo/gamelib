package com.gamelibrary.kafka.event;

import java.time.Instant;

public record GameCreatedEvent(
        String eventId,
        Instant occurredAt,
        String gameId,
        String title,
        String platform,
        int releaseYear
) implements GameEvent {
}
