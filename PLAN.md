# Game Library — Consolidated Plan

Single plan covering four workstreams: bug fixes, missing server endpoints,
webapp → backend wiring, and i18n. Ordered so each phase leaves the repo in a
working, verifiable state.

---

## 0. Decision log (locked)

| Decision | Choice |
|---|---|
| Webapp → backend wiring | **Option A**: `db.ts` internals become HTTP calls to the Spring REST API; PostgreSQL is the single source of truth. Kafka stays server-side (publish domain events after writes), as today. |
| Game IDs | **Slugs everywhere** (`hollow-knight`). Server already slugifies titles; seed data already uses slugs; webapp's `addGame` stops generating UUIDs (the server generates the id now). |
| coverUrl | **Left out of the server schema for now.** The webapp keeps the field in its TS type; it stays `undefined`, so the checkerboard placeholder keeps rendering. Revisit when real cover art arrives (would need a `V3` migration + entity/DTO fields). |
| i18n | **Spanish + English**, path-prefixed, via `astro-i18n-aut@^0.8.1` (peer-deps include Astro 7). Spanish = default locale at root; English under `/en/*`. |
| Auth | **None yet** — API stays open on localhost, matching the README's "auth deferred" decision. |
| Duplicate-title behavior | Webapp pre-checks by title (GET list → find) before POST, preserving today's "silently return existing game" UX. Server keeps its 400-on-existing-slug as the backstop. |
| Seed drift (`PAUSED` vs `wishlist`) | Fixed by a new `V3` migration aligning the server seed with the current `webapp/data/*.json`. |

**Target architecture after all phases:**

```
Browser ──▶ Astro (SSR, /en + /) ──HTTP──▶ Spring REST ──▶ PostgreSQL
                                          │
                                          └──▶ Kafka topics ──▶ consumers
```

The webapp never touches Kafka or Postgres directly; JSON files stop being
written and remain only as a historical seed reference.

---

## Phase 0 — Prerequisites & baseline verification

The server is "built but unverified" because the toolchain was missing. Verify
before changing anything, so later failures are attributable.

1. Install the toolchain:
   - JDK 25 (Corretto) — `java -version`
   - Maven — `winget install Apache.Maven` — `mvn -v`
   - Docker Desktop (only for running the app; tests don't need it)
2. Run the backend test suite: `cd server && mvn test`
   - Expected: **one failure** — `GameLibraryIntegrationTest` asserts
     `status()).isEqualTo("PLAYING")` where `status()` is a `GameStatus` enum,
     not a String (fixed in Phase 1, step 1.1).
3. Boot smoke test: `docker compose up -d` then `mvn spring-boot:run`;
   hit `GET http://localhost:8080/api/games` and `/actuator/health`.
4. Webapp smoke: `cd webapp && npm install && npm run dev` — full user flow
   (entrar → status → list → profile) still works on JSON.

**Exit criteria:** toolchain installed; exactly the known test failure
reproduced; both apps run.

---

## Phase 1 — Bug fixes

Three bugs found during the code review of `repomix-output.xml`.

### 1.1 Failing integration-test assertion (server)

`server/src/test/java/com/gamelibrary/GameLibraryIntegrationTest.java`:

```java
// before (enum vs String — always fails)
assertThat(status.getBody().status()).isEqualTo("PLAYING");
// after
assertThat(status.getBody().status()).isEqualTo(GameStatus.PLAYING);
```

Add the `GameStatus` import. Re-run `mvn test` → suite green.

### 1.2 Broken year input (webapp)

`webapp/src/pages/games/nuevo.astro`:

- The year field is `<input type="date">` but the action validates
  `z.coerce.number()` — a date string coerces to `NaN` → every submit with a
  year fails validation. Change to `<input type="number" min="1970" max="2100"
  placeholder="Ej: 1998">` (also fixes the copy-pasted platform placeholder
  text).
- Empty-input coercion trap: `z.coerce.number()` turns `""` into `0`, so an
  empty year would be stored as `0`. In `webapp/src/actions/index.ts`, use a
  preprocess that maps `""` → `undefined`:

  ```ts
  year: z.preprocess(
    (v) => (v === '' || v == null ? undefined : v),
    z.coerce.number().int().min(1970).max(2100).optional()
  ),
  ```

- Restore `required` on the title input (present in the guide, lost in the
  final page; Zod's `min(1)` is the backstop, `required` is the UX).

### 1.3 Slugify mangles accented titles (server)

`server/src/main/java/com/gamelibrary/api/GameController.java` —
`slugify("Pokémon")` currently yields `pok- mon`-style garbage because
`[^a-z0-9]` strips accented letters. For a Spanish-language app this hits
often ("Pokémon", "Zelda: A Link to the Past" is fine but "Bayonetta:
Época" is not). Fix:

```java
private static String slugify(String title) {
    String slug = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")        // strip diacritics: é → e
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    if (slug.isBlank()) {
        throw new IllegalArgumentException("Title produces an empty id: " + title);
    }
    return slug;
}
```

(`\\p{M}` = Unicode combining marks, which NFD decomposition produces.)

Add unit coverage in `GameServiceTest` or a small controller test:
`"Pokémon"` → `pokemon`, `"Época!!"` → `epoca`, `"!!!"` → rejected.

**Exit criteria:** `mvn test` fully green; adding a game with a year and with
an accented title works end-to-end in the webapp (still on JSON for now).

---

## Phase 2 — Missing server endpoints

The webapp UI supports `deleteList`, `addGameToList`, `removeGameFromList` —
the server only has create/get for lists. Add parity so Phase 3 has an
endpoint for every action.

### 2.1 New endpoints (`ListController`)

| Method | Path | Behavior |
|---|---|---|
| DELETE | `/api/lists/{id}` | Delete list (cascade removes `game_list_games` rows per FK). 204. 404 if unknown. |
| PUT | `/api/lists/{id}/games/{gameId}` | Add game to list. Idempotent (204 whether or not it was present). 404 if list or game unknown. |
| DELETE | `/api/lists/{id}/games/{gameId}` | Remove game from list. Idempotent (204 if absent). 404 if list unknown. |

### 2.2 Service + entity changes

`ListService`:

- `delete(UUID id)` — `findById` → 404 via `NotFoundException`, then delete;
  publish `ListDeletedEvent`.
- `addGame(UUID listId, String gameId)` / `removeGame(UUID listId, String gameId)`
  — mutate the `gameIds` set (JPA dirty-checking persists the element
  collection), then publish `listUpdated(saved)`.

`GameList` entity: add `addGameId(String)` / `removeGameId(String)` methods
instead of letting services mutate the raw set (keeps the mutable collection
encapsulated).

### 2.3 New event type

`kafka/event/ListDeletedEvent.java` — record implementing the sealed
`GameEvent` (`eventId, occurredAt, listId, username, name`). Add to the
`permits` clause, publish on topic `game-library.lists.deleted` (declare a
`NewTopic` in `KafkaConfig`, 3 partitions / 1 replica like the others; add the
topic to `@EmbeddedKafka` in the integration test). Add a logging listener in
`EventConsumer`.

### 2.4 Seed-drift fix

`server/src/main/resources/db/migration/V3__align_seed_with_json.sql`:

```sql
-- library.json (the live data at wiring time) says 'wishlist'; V2 seeded 'PAUSED'.
UPDATE library_entries SET status = 'WISHLIST'
WHERE username = 'ash' AND game_id = 'hollow-knight';
```

### 2.5 Tests

- `ListServiceTest` (Mockito): delete happy-path + 404; add/remove happy-path;
  add with unknown game → 404; events published.
- Extend `GameLibraryIntegrationTest`: create list → add game (PUT) → fetch
  shows it → add same game again (still 1 entry, idempotent) → remove game →
  delete list → GET returns 404.

**Exit criteria:** `mvn test` green with new coverage; `curl` walkthrough of
the three new endpoints against the Dockerized stack.

---

## Phase 3 — Webapp → backend wiring (Option A)

The migration seam is `webapp/src/lib/db.ts` — pages and actions keep calling
it; only its internals change.

### 3.1 New API client — `webapp/src/lib/api.ts`

- Base URL from env: `process.env.API_BASE_URL ?? 'http://localhost:8080'`.
  (Server-side only — all callers are SSR frontmatter/actions, so no
  `PUBLIC_` prefix and no client exposure. Add `API_BASE_URL=` to
  `webapp/.env`, which is already gitignored.)
- Typed `fetch` wrappers for every endpoint in the server README table, plus
  the Phase 2 endpoints. Throw `ApiError` (status + message from the server's
  `ApiError` JSON body) on non-2xx.
- **Field/status mapping** (this is the model-drift reconciliation):

  | Webapp | Server |
  |---|---|
  | `GameStatus` lowercase (`'playing'`) | `GameStatus` enum uppercase (`PLAYING`) — map on the way in/out of every library call |
  | `Game.year` | `GameResponse.releaseYear` |
  | `Game.coverUrl` | *(no server field — always `undefined` on read; placeholder covers still render)* |
  | `GameList.gameIds: string[]` | `Set<String>` — serialize/deserialize |

- `searchGames(query)`: fetch all games, filter by title in the webapp (same
  behavior as today; the catalog is tiny — server-side search is a later
  optimization, noted but not built).

### 3.2 Rewrite `db.ts` internals, keep the surface

Every exported function keeps its signature so **no page or action imports
change** (except the `addGame` dedupe note below):

- `getUsers`/`getUser` → `GET /api/users`, `GET /api/users/{username}`
- `getGames`/`getGame`/`searchGames` → `/api/games*` (+ client-side filter)
- `addGame` → first find-by-title via `getGames()` (preserves today's
  return-existing UX), else `POST /api/games` — **the server generates the
  slug id**, so delete the `randomUUID()` logic. The created game's id is the
  slug the server returns.
- `getLibrary`/`getLibraryForUser`/`getStatus` → `GET /api/library/{username}`
  (+ status mapping)
- `setStatus` → `PUT /api/library/{u}/games/{g}/status/{STATUS}` (uppercase)
- `getListsForUser`/`getList` → `/api/lists*`
- `createList` → `POST /api/lists/user/{username}`
- `deleteList` → `DELETE /api/lists/{id}`
- `addGameToList` / `removeGameFromList` → the Phase 2 PUT/DELETE endpoints
  (idempotent server-side, matching current no-op-on-repeat behavior)

Remove all `node:fs` imports from `db.ts`; the file becomes pure HTTP +
mapping. Keep the `Game`/`User`/`GameList`/`LibraryEntry` TS interfaces as the
webapp's canonical shapes.

### 3.3 Error handling

Astro Actions already surface errors only in the server console (documented
convention). During wiring, make that explicit per mutation: wrap API calls so
a failed fetch logs `console.error` with the endpoint + server message. No new
UX (a toast system is out of scope).

### 3.4 Docs & cleanup

- Root `README.md`: status table → "Webapp → backend wiring: Done (Option A)";
  note JSON files are no longer written.
- `webapp/README.md` + `AGENTS.md`: data layer section now says "all reads and
  writes go through `lib/api.ts` → Spring REST; `lib/db.ts` is the thin
  facade pages keep using". Document `API_BASE_URL`.
- `data/*.json`: keep in the repo as the historical seed reference; add a
  header comment in docs that they are frozen (V2/V3 own the seed now).
- Add `repomix-output.xml` to `.gitignore` (generated artifact; repomix itself
  warns it can contain sensitive merged content).

### 3.5 Verification (the important bit)

1. `docker compose up -d` + `mvn spring-boot:run` + `npm run dev`.
2. Full user flow on JSON-era muscle memory: entrar como Ash → cambiar estado
   → crear lista → añadir juego a la lista → borrar lista → entrar como Misty
   → ver `/users/ash` refleja los cambios.
3. Confirm every mutation lands in Postgres (`docker compose exec postgres
   psql ...`) and that consumer log lines appear in the server console (Kafka
   events firing).
4. Confirm the webapp writes **zero** files under `data/` (e.g. note mtimes
   before/after).
5. Repeat the Playwright-style checks from `ANALISIS.md` §8: no console
   errors, 375/768/1280 screenshots unchanged.

**Exit criteria:** the app behaves identically to before, but every read and
write round-trips through Spring + Postgres; JSON files untouched.

---

## Phase 4 — i18n (Spanish + English, path-prefixed)

### 4.1 Setup

```bash
cd webapp && npm install astro-i18n-aut@^0.8.1
```

`astro.config.mjs`:

```js
import { i18n } from 'astro-i18n-aut/integration';

export default defineConfig({
  // ...existing output/vite/adapter...
  integrations: [
    i18n({
      locales: { es: 'es-ES', en: 'en-US' },
      defaultLocale: 'es',
      redirectDefaultLocale: true, // /es/games → /games (canonical root)
    }),
  ],
});
```

Resulting URLs: Spanish at root (`/games`), English prefixed (`/en/games`).
`/es/*` redirects to the root equivalent — one canonical URL per page per
locale. All current pages are `.astro` files, which is the only page type
astro-i18n-aut translates — no exclusions needed.

### 4.2 Dictionaries — `webapp/src/i18n/`

- `es.ts` — the source of truth for keys (Spanish is the current hardcoded
  copy, so extract it verbatim).
- `en.ts` — `satisfies Dictionary` type-checked against `es.ts`, so a missing
  key is a **compile error**, not a runtime blank.
- `index.ts` — `type Dictionary`, `getDictionary(locale)` helper.

Key groups (extract every hardcoded UI string):

| Group | Examples |
|---|---|
| Nav/layout | Juegos/Games, Listas/Lists, Usuarios/Users, Entrar/Sign in, `Volver atrás`/Back |
| Landing | hero title (stays "GAME LIBRARY"), subtitle, the three info panels |
| Games | `Buscar videojuego…`, `AÑADIR JUEGO`, form labels, empty states, `Solicitar añadirlo` |
| Statuses | wishlist/backlog/playing/beaten/paused/quit → translated **labels** (`Wishlist`, `Backlog`, `Playing`, `Beaten`, `Paused`, `Quit` — the EN words mostly match; the badge/selector keep the raw status as the form `value`, only the display text is translated) |
| Lists | `Mis listas`/My lists, `CREAR`, `ELIMINAR LISTA`, `EN LA LISTA`, `AÑADIR OTROS JUEGOS` |
| Users | `¿Quién eres?`/Who are you?, `LISTAS` |
| Errors | `Revisa los datos del formulario.` |

**Not translated:** game titles/descriptions, list names, usernames — that's
user data, stored as-is. (Note in docs: a future enhancement could store
per-locale content; out of scope.)

### 4.3 Wiring it into pages

- Locale access: `Astro.currentLocale ?? 'es'` (set by the integration per
  render); a tiny `getDict(Astro)` helper in `i18n/index.ts` returning the
  loaded dictionary keeps frontmatter one line.
- `<html lang={locale}>` in `Layout.astro` (currently hardcoded `lang="es"`).
- Language switcher in the header: `ES | EN` links built with
  `getRelativeLocaleUrl`-style helpers — same path, other locale
  (`/games` ↔ `/en/games`), preserving the current route.
- Components that render strings (`StatusBadge`, `StatusSelector`,
  `ListCard`, `CoverPlaceholder`'s aria-label) receive the translated label or
  load the dict themselves — prefer passing the label as a prop from the page
  (keeps components locale-agnostic where easy; `StatusBadge` is used from 3
  pages, so passing `label` keeps it simple).

### 4.4 Gotchas to respect (from this repo's own history)

- **No prerendering on any page** — sessions + now locale resolution are
  per-request (AGENTS.md's hard-learned rule; unchanged).
- Scripts (`back-btn`, games view toggle) are locale-independent — no changes,
  but re-test them under `/en/*` since they re-bind on `astro:page-load`.
- Forms POST to Actions with hidden inputs — locale-neutral, but verify the
  redirect after `addGame` (`/games/{slug}`) lands on the **root-locale**
  route, which is valid for both locales.
- `localStorage` key `gamelib-games-view` stays shared across locales
  (deliberate).

### 4.5 Verification

- Browse every page in both locales; click through the full user flow in
  `/en/*`.
- `grep` the `src/pages` + `src/components` trees for remaining hardcoded
  Spanish outside dictionaries (catch stragglers like aria-labels).
- Check `<html lang>` flips correctly; language switcher round-trips the
  current path.
- 375px re-check: the new switcher must not recreate the header-overflow bug
  from `ANALISIS.md` §7.4.

**Exit criteria:** both locales fully browsable, no missing-key compile
errors, console clean, responsive checks pass.

---

## Cross-cutting concerns

### Suggested execution order & commits

One commit (or small commit group) per numbered step, so each is revertible:

```
Phase 0  (no commits — installs + baseline)
Phase 1  fix(server): enum assertion, slugify diacritics  |  fix(webapp): year input
Phase 2  feat(server): list delete + membership endpoints, ListDeletedEvent, V3 seed align
Phase 3  refactor(webapp): db.ts → Spring REST client (Option A)
Phase 4  feat(webapp): es/en i18n via astro-i18n-aut
         docs: READMEs + AGENTS.md updates (or folded into each phase)
```

### Risks & mitigations

| Risk | Mitigation |
|---|---|
| Java 25 / Maven quirks on Windows | Phase 0 exists to surface these before any code changes. |
| Slug collisions from distinct titles ("Pokémon" vs "Pokemon") | Webapp pre-checks by title; server 400s on existing slug. Rare for a solo catalog; documented as a known limitation (already in the server's code comment). |
| Astro Actions hide errors from users | Pre-existing convention; Phase 3 only improves console detail. A user-visible error surface is explicitly out of scope. |
| astro-i18n-aut behavior with SSR + ClientRouter | Phase 4 verification re-runs the repo's proven Playwright-style checks under `/en/*`. |
| Losing the JSON rollback path | JSON files are kept frozen in-repo; rolling back Phase 3 is a git revert, and data written during API-era testing can be re-seeded via Flyway. |

### Explicitly out of scope

- Auth (session cookies), BFF layer, real cover art, pagination, server-side
  search, user-visible error toasts, translating user-generated content.
