package com.gamelibrary.api;

import com.gamelibrary.api.dto.LibraryEntryResponse;
import com.gamelibrary.domain.GameStatus;
import com.gamelibrary.service.LibraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping("/{username}")
    public List<LibraryEntryResponse> library(@PathVariable String username) {
        return libraryService.findLibrary(username).stream().map(LibraryEntryResponse::from).toList();
    }

    /**
     * PUT with the status in the path keeps the call idempotent — retrying
     * the same request is safe (mirrors webapp's setStatus action).
     */
    @PutMapping("/{username}/games/{gameId}/status/{status}")
    public LibraryEntryResponse setStatus(@PathVariable String username,
                                          @PathVariable String gameId,
                                          @PathVariable GameStatus status) {
        return LibraryEntryResponse.from(libraryService.setStatus(username, gameId, status));
    }
}
