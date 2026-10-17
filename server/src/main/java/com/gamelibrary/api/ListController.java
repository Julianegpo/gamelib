package com.gamelibrary.api;

import com.gamelibrary.api.dto.CreateListRequest;
import com.gamelibrary.api.dto.ListResponse;
import com.gamelibrary.domain.GameList;
import com.gamelibrary.service.ListService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lists")
public class ListController {

    private final ListService listService;

    public ListController(ListService listService) {
        this.listService = listService;
    }

    @GetMapping("/user/{username}")
    public List<ListResponse> byUser(@PathVariable String username) {
        return listService.findByUser(username).stream().map(ListResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ListResponse get(@PathVariable UUID id) {
        return ListResponse.from(listService.findById(id));
    }

    @PostMapping("/user/{username}")
    @ResponseStatus(HttpStatus.CREATED)
    public ListResponse create(@PathVariable String username,
                               @Valid @RequestBody CreateListRequest request) {
        GameList created = listService.create(username, request.name(), request.description(),
                request.gameIds() == null ? java.util.Set.of() : request.gameIds());
        return ListResponse.from(created);
    }
}
