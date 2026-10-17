package com.gamelibrary.kafka;

import com.gamelibrary.domain.Game;
import com.gamelibrary.domain.GameList;
import com.gamelibrary.domain.LibraryEntry;
import com.gamelibrary.kafka.event.GameCreatedEvent;
import com.gamelibrary.kafka.event.GameEvent;
import com.gamelibrary.kafka.event.LibraryStatusChangedEvent;
import com.gamelibrary.kafka.event.ListUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes domain events after the service layer has committed state to the DB.
 * Uses fire-and-forget sends with a callback: a failed publish is logged, not
 * rolled back (documented trade-off; see README "Known trade-offs" for the
 * outbox pattern that fixes this).
 */
@Component
public class EventProducer {

    private static final Logger log = LoggerFactory.getLogger(EventProducer.class);

    private final KafkaTemplate<String, GameEvent> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, GameEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void gameCreated(Game game) {
        publish("game-library.games.created", game.getId(), new GameCreatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                game.getId(),
                game.getTitle(),
                game.getPlatform(),
                game.getReleaseYear()
        ));
    }

    public void libraryStatusChanged(LibraryEntry entry) {
        // Partition by username: all of a user's events share one partition,
        // giving per-user ordering guarantees.
        publish("game-library.library.status-changed", entry.getUsername(), new LibraryStatusChangedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                entry.getUsername(),
                entry.getGameId(),
                entry.getStatus()
        ));
    }

    public void listUpdated(GameList list) {
        publish("game-library.lists.updated", list.getUsername(), new ListUpdatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                list.getId(),
                list.getUsername(),
                list.getName(),
                List.copyOf(list.getGameIds())
        ));
    }

    private void publish(String topic, String key, GameEvent event) {
        kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event {} to topic {}", event.eventId(), topic, ex);
            } else {
                log.info("Published event {} to topic {} partition {} offset {}",
                        event.eventId(), topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
