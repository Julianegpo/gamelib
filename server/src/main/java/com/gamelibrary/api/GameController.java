package com.gamelibrary.api;

import com.gamelibrary.api.dto.CreateGameRequest;
import com.gamelibrary.api.dto.GameResponse;
import com.gamelibrary.domain.Game;
import com.gamelibrary.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public List<GameResponse> list() {
        return gameService.findAll().stream().map(GameResponse::from).toList();
    }

    @GetMapping("/{id}")
    public GameResponse get(@PathVariable String id) {
        return GameResponse.from(gameService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameResponse create(@Valid @RequestBody CreateGameRequest request) {
        Game game = new Game(
                slugify(request.title()),
                request.title(),
                request.platform(),
                request.releaseYear(),
                request.description()
        );
        return GameResponse.from(gameService.create(game));
    }

    @PutMapping("/{id}")
    public GameResponse update(@PathVariable String id, @Valid @RequestBody CreateGameRequest request) {
        Game updated = new Game(id, request.title(), request.platform(),
                request.releaseYear(), request.description());
        return GameResponse.from(gameService.update(id, updated));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        gameService.delete(id);
    }

    /**
     * Derive the slug from the title, matching the webapp's id convention
     * ("Hollow Knight" -> "hollow-knight"). Not collision-proof — for
     * uniqueness we rely on the existsById check + 409 conflict.
     */
    private static String slugify(String title) {
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
