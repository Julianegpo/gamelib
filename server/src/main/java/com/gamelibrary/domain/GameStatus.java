package com.gamelibrary.domain;

/**
 * Game statuses, mirroring the webapp's GameStatus type
 * ('wishlist' | 'backlog' | 'playing' | 'beaten' | 'paused' | 'quit').
 */
public enum GameStatus {
    WISHLIST,
    BACKLOG,
    PLAYING,
    BEATEN,
    PAUSED,
    QUIT
}
