package com.gamelibrary.service;

import com.gamelibrary.domain.GameList;
import com.gamelibrary.error.NotFoundException;
import com.gamelibrary.kafka.EventProducer;
import com.gamelibrary.repository.GameListRepository;
import com.gamelibrary.repository.GameRepository;
import com.gamelibrary.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ListService {

    private final GameListRepository listRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final EventProducer eventProducer;

    public ListService(GameListRepository listRepository,
                       UserRepository userRepository,
                       GameRepository gameRepository,
                       EventProducer eventProducer) {
        this.listRepository = listRepository;
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional(readOnly = true)
    public List<GameList> findByUser(String username) {
        return listRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public GameList findById(UUID id) {
        return listRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("List", id));
    }

    @Transactional
    public GameList create(String username, String name, String description, Set<String> gameIds) {
        if (!userRepository.existsById(username)) {
            throw new NotFoundException("User", username);
        }
        for (String gameId : gameIds) {
            if (!gameRepository.existsById(gameId)) {
                throw new NotFoundException("Game", gameId);
            }
        }
        GameList saved = listRepository.save(new GameList(UUID.randomUUID(), username, name, description, gameIds));
        eventProducer.listUpdated(saved);
        return saved;
    }
}
