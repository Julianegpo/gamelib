# Game Library — Webapp

Astro 7 frontend for the game library: browse games, manage your library
statuses, and build curated lists. SSR via the Node adapter, styled with
Tailwind CSS, persisted to JSON files.

> Part of the `gamelib` monorepo — see the [root README](../README.md) for the
> full architecture and migration plan toward the Spring Boot + Kafka backend
> in `../server/`.

## Commands

```bash
npm install        # Install deps (astro + tailwindcss)
npm run dev        # Dev server at http://localhost:4321
npm run build      # SSR build (Node adapter, standalone)
npm run preview    # Preview the build locally
npm run astro ...  # Astro CLI
```

## How it works

- **Single data layer**: all file I/O goes through `src/lib/db.ts`. Nothing
  else touches the filesystem directly — the migration seam for the real API.
- **Persistence**: four JSON files in `data/` (`games.json`, `users.json`,
  `library.json`, `lists.json`). No relational joins — ID lookup by convention.
- **Mutations**: server-side Astro Actions in `src/actions/index.ts`
  (create/update games, set library status, manage lists).
- **Sessions**: user "login" is session-based selection on `/entrar` with no
  passwords — deliberate scope decision, not an oversight.
- **Rendering**: `output: 'server'` with `@astrojs/node` (standalone). Pages
  using `Astro.session` (`/entrar`, `/usuarios/*`) must never be prerendered.

## Pages

| Route | What it does |
|-------|--------------|
| `/` | Landing page with your library overview |
| `/juegos`, `/juegos/[id]` | Game catalog, details, status selector |
| `/juegos/nuevo` | Add a game |
| `/usuarios`, `/usuarios/[username]` | Users and their libraries |
| `/listas`, `/listas/[id]` | Curated game lists |
| `/entrar` | Pick the active user (session) |

## Data model

- **Game** — slug id (`hollow-knight`), title, platform, year, description
- **User** — username + display name
- **Library entry** — `(username, gameId)` → status:
  `wishlist | backlog | playing | beaten | paused | quit`
- **List** — user-owned named collection of game ids

Seed data lives in `data/*.json` and is mirrored in the backend's Flyway
migration (`server/src/main/resources/db/migration/V2__seed_data.sql`) —
keep both in sync until the backend owns state.

## Conventions (learned the hard way)

- Errors in Actions surface in the server console, not the HTML page — don't
  rely on try/catch rendering feedback to the user.
- `data/*.json` has no write locking: safe for a single dev process, not for
  concurrency. That's exactly what the Kafka migration fixes.
- Agent-specific context lives in [AGENTS.md](./AGENTS.md) — architecture
  analysis, Kafka primer, and the decision log.
