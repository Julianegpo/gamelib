package com.gamelibrary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A user's status for a game, e.g. ash + hollow-knight -> PAUSED.
 * The composite key (username, gameId) mirrors library.json.
 */
@Entity
@Table(name = "library_entries")
public class LibraryEntry {

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GameStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LibraryEntry() {
        // JPA needs a no-arg constructor
    }

    public LibraryEntry(String username, String gameId, GameStatus status, Instant updatedAt) {
        this.id = new Key(username, gameId);
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getUsername() {
        return id.username();
    }

    public String getGameId() {
        return id.gameId();
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "username", nullable = false, length = 50)
        private String username;

        @Column(name = "game_id", nullable = false, length = 100)
        private String gameId;

        protected Key() {
            // JPA needs a no-arg constructor
        }

        public Key(String username, String gameId) {
            this.username = username;
            this.gameId = gameId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(username, key.username) && Objects.equals(gameId, key.gameId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(username, gameId);
        }
    }
}
