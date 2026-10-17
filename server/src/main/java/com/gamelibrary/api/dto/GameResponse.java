package com.gamelibrary.api.dto;

import com.gamelibrary.domain.Game;

public record GameResponse(String id, String title, String platform, int releaseYear, String description) {

    public static GameResponse from(Game game) {
        return new GameResponse(game.getId(), game.getTitle(), game.getPlatform(),
                game.getReleaseYear(), game.getDescription());
    }
}
