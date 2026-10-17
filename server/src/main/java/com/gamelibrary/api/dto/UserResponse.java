package com.gamelibrary.api.dto;

import com.gamelibrary.domain.User;

public record UserResponse(String username, String displayName) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getUsername(), user.getDisplayName());
    }
}
