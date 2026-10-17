-- Initial data migrated from webapp/data/*.json so the app works out of
-- the box with the same content the Astro frontend had.

INSERT INTO games (id, title, platform, release_year, description) VALUES
    ('hollow-knight', 'Hollow Knight', 'PC', 2017,
     'Metroidvania en 2D con combate preciso y un mundo interconectado enorme.'),
    ('chrono-trigger', 'Chrono Trigger', 'SNES', 1995,
     'JRPG clásico con viajes en el tiempo y múltiples finales.');

INSERT INTO users (username, display_name) VALUES
    ('ash', 'Ash'),
    ('misty', 'Misty');

INSERT INTO library_entries (username, game_id, status, updated_at) VALUES
    ('ash', 'hollow-knight', 'PAUSED', '2026-10-14T21:20:15.464Z'),
    ('ash', 'chrono-trigger', 'BEATEN', '2026-09-15T10:00:00.000Z'),
    ('misty', 'hollow-knight', 'BEATEN', '2026-10-13T08:14:38.871Z');

INSERT INTO game_lists (id, username, name, description) VALUES
    ('0b6f1f47-2d47-4f9a-941c-1e85f6a5b001', 'ash', 'Mis platinos', 'Juegos que me he terminado al 100%.'),
    ('cfeea5c2-4f54-46a3-8337-eb78869b8ec7', 'misty', 'fisicos', NULL);

INSERT INTO game_list_games (list_id, game_id) VALUES
    ('0b6f1f47-2d47-4f9a-941c-1e85f6a5b001', 'chrono-trigger'),
    ('0b6f1f47-2d47-4f9a-941c-1e85f6a5b001', 'hollow-knight'),
    ('cfeea5c2-4f54-46a3-8337-eb78869b8ec7', 'hollow-knight');
