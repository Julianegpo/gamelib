package com.gamelibrary.service;

import com.gamelibrary.domain.Game;
import com.gamelibrary.error.NotFoundException;
import com.gamelibrary.kafka.EventProducer;
import com.gamelibrary.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private EventProducer eventProducer;

    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameService = new GameService(gameRepository, eventProducer);
    }

    @Test
    void createSavesGameAndPublishesEvent() {
        Game game = new Game("hollow-knight", "Hollow Knight", "PC", 2017, "desc");
        when(gameRepository.existsById("hollow-knight")).thenReturn(false);
        when(gameRepository.save(game)).thenReturn(game);

        Game result = gameService.create(game);

        assertThat(result.getId()).isEqualTo("hollow-knight");
        verify(gameRepository).save(game);
        verify(eventProducer).gameCreated(game);
    }

    @Test
    void createRejectsDuplicateId() {
        Game game = new Game("hollow-knight", "Hollow Knight", "PC", 2017, "desc");
        when(gameRepository.existsById("hollow-knight")).thenReturn(true);

        assertThatThrownBy(() -> gameService.create(game))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(gameRepository, never()).save(any());
        verify(eventProducer, never()).gameCreated(any());
    }

    @Test
    void findByIdThrowsNotFoundForUnknownGame() {
        when(gameRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.findById("nope"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void updateMutatesExistingGame() {
        Game existing = new Game("hollow-knight", "Hollow Knight", "PC", 2017, "old");
        Game updated = new Game("hollow-knight", "Hollow Knight", "Switch", 2018, "new");
        when(gameRepository.findById("hollow-knight")).thenReturn(Optional.of(existing));

        Game result = gameService.update("hollow-knight", updated);

        assertThat(result.getPlatform()).isEqualTo("Switch");
        assertThat(result.getReleaseYear()).isEqualTo(2018);
        assertThat(result.getDescription()).isEqualTo("new");
    }
}
