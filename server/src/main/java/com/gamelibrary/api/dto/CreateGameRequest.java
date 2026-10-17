package com.gamelibrary.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGameRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 50) String platform,
        @Min(1970) @Max(2100) int releaseYear,
        @Size(max = 2000) String description
) {
}
