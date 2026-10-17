package com.gamelibrary.service;

import com.gamelibrary.domain.GameStatus;
import com.gamelibrary.domain.LibraryEntry;
import com.gamelibrary.error.NotFoundException;
import com.gamelibrary.kafka.EventProducer;
import com.gamelibrary.repository.GameRepository;
import com.gamelibrary.repository.LibraryEntryRepository;
import com.gamelibrary.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class LibraryService {

    private final LibraryEntryRepository libraryRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final EventProducer eventProducer;

    public LibraryService(LibraryEntryRepository libraryRepository,
                          UserRepository userRepository,
                          GameRepository gameRepository,
                          EventProducer eventProducer) {
        this.libraryRepository = libraryRepository;
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional(readOnly = true)
    public List<LibraryEntry> findLibrary(String username) {
        if (!userRepository.existsById(username)) {
            throw new NotFoundException("User", username);
        }
        return libraryRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public GameStatus getStatus(String username, String gameId) {
        return libraryRepository.findByUsername(username).stream()
                .filter(e -> e.getGameId().equals(gameId))
                .findFirst()
                .map(LibraryEntry::getStatus)
                .orElse(null);
    }

    /**
     * Set (or change) a user's status for a game, then publish the change.
     * Upsert semantics — matches db.ts setStatus behaviour.
     */
    @Transactional
    public LibraryEntry setStatus(String username, String gameId, GameStatus status) {
        if (!userRepository.existsById(username)) {
            throw new NotFoundException("User", username);
        }
        if (!gameRepository.existsById(gameId)) {
            throw new NotFoundException("Game", gameId);
        }

        LibraryEntry entry = libraryRepository.findById(new LibraryEntry.Key(username, gameId))
                .map(existing -> {
                    existing.setStatus(status);
                    existing.setUpdatedAt(Instant.now());
                    return existing;
                })
                .orElseGet(() -> new LibraryEntry(username, gameId, status, Instant.now()));

        LibraryEntry saved = libraryRepository.save(entry);
        eventProducer.libraryStatusChanged(saved);
        return saved;
    }
}
