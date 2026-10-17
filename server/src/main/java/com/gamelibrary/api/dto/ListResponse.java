package com.gamelibrary.api.dto;

import com.gamelibrary.domain.GameList;

import java.util.Set;
import java.util.UUID;

public record ListResponse(UUID id, String username, String name, String description, Set<String> gameIds) {

    public static ListResponse from(GameList list) {
        return new ListResponse(list.getId(), list.getUsername(), list.getName(),
                list.getDescription(), Set.copyOf(list.getGameIds()));
    }
}
