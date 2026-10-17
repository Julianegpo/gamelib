package com.gamelibrary.api.dto;

import com.gamelibrary.domain.GameStatus;
import com.gamelibrary.domain.LibraryEntry;

import java.time.Instant;

public record LibraryEntryResponse(String username, String gameId, GameStatus status, Instant updatedAt) {

    public static LibraryEntryResponse from(LibraryEntry entry) {
        return new LibraryEntryResponse(entry.getUsername(), entry.getGameId(),
                entry.getStatus(), entry.getUpdatedAt());
    }
}
