package com.gamelibrary;

import com.gamelibrary.api.dto.CreateGameRequest;
import com.gamelibrary.api.dto.CreateListRequest;
import com.gamelibrary.api.dto.CreateUserRequest;
import com.gamelibrary.api.dto.GameResponse;
import com.gamelibrary.api.dto.LibraryEntryResponse;
import com.gamelibrary.api.dto.ListResponse;
import com.gamelibrary.api.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack test without Docker: H2 in PostgreSQL mode (same Flyway
 * migrations as Postgres) + embedded Kafka broker.
 * Verifies the REST API writes state and events round-trip through Kafka
 * into the consumers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {
        "game-library.games.created",
        "game-library.library.status-changed",
        "game-library.lists.updated"
})
class GameLibraryIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void seededGamesAreListed() {
        ResponseEntity<GameResponse[]> response = rest.getForEntity(url("/api/games"), GameResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(GameResponse::id)
                .contains("hollow-knight", "chrono-trigger");
    }

    @Test
    void createUserAndSetLibraryStatusRoundTrip() {
        ResponseEntity<UserResponse> created = rest.postForEntity(url("/api/users"),
                new HttpEntity<>(new CreateUserRequest("brock", "Brock")), UserResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<LibraryEntryResponse> status = rest.exchange(
                url("/api/library/brock/games/hollow-knight/status/PLAYING"),
                HttpMethod.PUT, HttpEntity.EMPTY, LibraryEntryResponse.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody()).isNotNull();
        assertThat(status.getBody().status()).isEqualTo("PLAYING");

        ResponseEntity<LibraryEntryResponse[]> library =
                rest.getForEntity(url("/api/library/brock"), LibraryEntryResponse[].class);
        assertThat(library.getBody()).extracting(LibraryEntryResponse::gameId).containsExactly("hollow-knight");
    }

    @Test
    void createGameEmitsEventAndReturnsSlugId() {
        ResponseEntity<GameResponse> response = rest.postForEntity(url("/api/games"),
                new HttpEntity<>(new CreateGameRequest("Celeste", "PC", 2018, "Indie platformer")),
                GameResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo("celeste");

        // Second create with same title -> 400 (slug collision caught by existsById)
        ResponseEntity<String> duplicate = rest.postForEntity(url("/api/games"),
                new HttpEntity<>(new CreateGameRequest("Celeste", "PC", 2018, "Indie platformer")),
                String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createListAndFetchItBack() {
        ResponseEntity<ListResponse> response = rest.postForEntity(url("/api/lists/user/ash"),
                new HttpEntity<>(new CreateListRequest("Favorites", "Best games", Set.of("chrono-trigger"))),
                ListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().gameIds()).containsExactly("chrono-trigger");

        ResponseEntity<ListResponse> fetched =
                rest.getForEntity(url("/api/lists/" + response.getBody().id()), ListResponse.class);
        assertThat(fetched.getBody()).isNotNull();
        assertThat(fetched.getBody().name()).isEqualTo("Favorites");
    }

    @Test
    void unknownGameReturns404() {
        ResponseEntity<String> response = rest.getForEntity(url("/api/games/nope"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
