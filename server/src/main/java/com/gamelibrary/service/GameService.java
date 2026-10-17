package com.gamelibrary.service;

import com.gamelibrary.domain.Game;
import com.gamelibrary.error.NotFoundException;
import com.gamelibrary.kafka.EventProducer;
import com.gamelibrary.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GameService {

    private final GameRepository gameRepository;
    private final EventProducer eventProducer;

    public GameService(GameRepository gameRepository, EventProducer eventProducer) {
        this.gameRepository = gameRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional(readOnly = true)
    public List<Game> findAll() {
        return gameRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Game findById(String id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Game", id));
    }

    /**
     * Create a game, then publish a GameCreatedEvent.
     * Note the dual-write trade-off (DB + Kafka): if publishing fails after
     * commit, the event is lost — acceptable for MVP, see README.
     */
    @Transactional
    public Game create(Game game) {
        if (gameRepository.existsById(game.getId())) {
            throw new IllegalArgumentException("Game already exists: " + game.getId());
        }
        Game saved = gameRepository.save(game);
        eventProducer.gameCreated(saved);
        return saved;
    }

    @Transactional
    public Game update(String id, Game updated) {
        Game existing = findById(id);
        existing.setTitle(updated.getTitle());
        existing.setPlatform(updated.getPlatform());
        existing.setReleaseYear(updated.getReleaseYear());
        existing.setDescription(updated.getDescription());
        return existing;
    }

    @Transactional
    public void delete(String id) {
        Game game = findById(id);
        gameRepository.delete(game);
    }
}
