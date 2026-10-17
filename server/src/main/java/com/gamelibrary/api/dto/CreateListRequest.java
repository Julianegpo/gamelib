package com.gamelibrary.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateListRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        Set<String> gameIds
) {
}
