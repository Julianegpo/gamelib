package com.gamelibrary.kafka.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListUpdatedEvent(
        String eventId,
        Instant occurredAt,
        UUID listId,
        String username,
        String name,
        List<String> gameIds
) implements GameEvent {
}
