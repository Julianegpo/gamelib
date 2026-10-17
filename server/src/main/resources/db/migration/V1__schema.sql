-- Schema for game-library-server.
-- Mirrors the four JSON files the Astro webapp used: games, users,
-- library, lists.

CREATE TABLE games (
    id           VARCHAR(100) PRIMARY KEY,
    title        TEXT NOT NULL,
    platform     VARCHAR(50) NOT NULL,
    release_year INTEGER NOT NULL,
    description  TEXT
);

CREATE TABLE users (
    username     VARCHAR(50) PRIMARY KEY,
    display_name TEXT NOT NULL
);

CREATE TABLE library_entries (
    username  VARCHAR(50) NOT NULL REFERENCES users (username),
    game_id   VARCHAR(100) NOT NULL REFERENCES games (id),
    status    VARCHAR(20) NOT NULL CHECK (status IN ('WISHLIST', 'BACKLOG', 'PLAYING', 'BEATEN', 'PAUSED', 'QUIT')),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (username, game_id)
);

CREATE TABLE game_lists (
    id          UUID PRIMARY KEY,
    username    VARCHAR(50) NOT NULL REFERENCES users (username),
    name        TEXT NOT NULL,
    description TEXT
);

CREATE TABLE game_list_games (
    list_id UUID NOT NULL REFERENCES game_lists (id) ON DELETE CASCADE,
    game_id VARCHAR(100) NOT NULL REFERENCES games (id),
    PRIMARY KEY (list_id, game_id)
);

CREATE INDEX idx_library_entries_username ON library_entries (username);
CREATE INDEX idx_game_lists_username ON game_lists (username);
