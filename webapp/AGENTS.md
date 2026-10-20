# Game Library — Agent Guide

A minimal Astro 7 game-library app with file-based persistence. This guide helps agents avoid mistakes by sharing only hard-earned context from actual bugs, quirks, and conventions that emerged during development.

## Commands

```bash
npm install              # Install deps (astro + tailwindcss)
npm run dev              # Dev server at http://localhost:4321
npm run build            # SSR build to ./dist/
npm run preview          # Preview build locally
npm run astro ...        # Astro CLI (add, check, migrate, etc.)
```

## Architecture — The "How It Works" Stuff You Need to Know

- **Single data layer**: All file I/O goes through `src/lib/db.ts`. Nothing else in the codebase touches filesystem directly. Replace here only to migrate to SQLite or a real API.
- **Persistence**: Four JSON files in `data/` (`games.json`, `users.json`, `library.json`, `lists.json`). Each is an array read/written via `db.ts`. No relational joins — ID lookup by convention.
- **Actions & sessions**: Server-side Astro Actions (in `src/actions/index.ts`) handle mutations. User "login" is just session-based selection (`/login` page) with no password requirement — deliberate scope decision.
- **Rendering**: `output: 'server'` in `astro.config.mjs` with `@astrojs/node` adapter, mode `standalone`. Pages are dynamic; building generates a Node process, not static files. Do not rely on prerendering for session-dependent pages.

## Why SSR Now (Not Architectural Necessity)

Current setup uses Node.js adapter solely because Actions need synchronous per-request handlers. You can:
1. Keep SSR during transition (Actions → Kafka producer)
2. Drop SSR entirely when Java backend owns state (Astro becomes client-side static + API fetches)
3. Or hybrid: cache responses in memory, async emit to Kafka queue

**Key insight**: SSR here solves immediate developer convenience, not business requirements. State ownership is what truly matters, not the adapter.

## 3-Layer Architecture Planning (Frontend → BFF → Backend)

### Why Consider This Stack?

When migrating to Event-Driven architecture with Java + Spring Kafka:

```
┌──────────────┐     ┌─────────────────┐     ┌──────────┐
│   Astro 7    │─▶──▶│  BFF Layer      │─▶──▶│Spring+Kafka│
│ (Static/SSR) │     │ (NestJS/Astro   │     │ Backend   │
│              │◀────│ + Edge Functions │     │  Kafka    │
└──────────────┘     └─────────────────┘     └───────────┘
          ↑                     ↑                     ↑
      Rendering                State                   Events
      & UI                    Aggregation/             Persistence
                            Authorization               (events)
```

### Layer Responsibilities

**Frontend (Astro)**: Pure presentation layer. No mutations, no state persistence — only fetches from BFF via HTTP.

**BFF (Middle Layer - Future NestJS)**: Business logic, authentication, authorization, event publishing. Orchestrates calls to multiple microservices if needed. Owns session tokens or JWTs; knows exactly what API contracts your frontend needs.

**Backend (Java Spring Kafka)**: Event processing, data aggregation, business rules enforcement, persistence through Kafka consumers. Emits state changes back upstream via events.

## Spring Kafka Quick Reference (For Senior Developers New to ED)

### Core Concepts

- **Producer**: Application that sends messages to topics
- **Consumer**: Application registered to receive and process messages from topics  
- **Topic**: A logical channel for events, similar to a queue or log. Events flow unidirectionally through them.
- **Group ID**: Multiple consumers with same group ID form a consumer group — they share work via partition consumption
- **Offset & Commit**: Consume offset positions per partition; commit offsets to track where you've processed

### Exactly-Once Semantics

Spring Kafka doesn't magically provide exactly-once by itself. Use:
- **Idempotent consumers**: Never mutate same event twice differently. Re-run logic when consumer fails mid-processing.
- **Ack offsets**: Configure auto-commit=false, commit offsets manually after processing succeeds. On failure, rollback and discard message.
- **Transactional producers**: Enable transactional-id on KafkaTemplate to ensure producer atomicity across multiple sends.

### Partitioning Strategy

Partition key ensures related records end up on same partition (e.g., username → all events for same user on same partition).

## Agent-Specific Guidelines — Read Before Making Any Architectural Decisions

### 🚨 Actions Errors Show in Logs, Not HTML Pages

Astro Actions throw exceptions without returning full error messages to HTML page:
- Exception logs appear in server console output
- Page might render with missing data or failed mutations (no visible error feedback)
- Wrap critical mutations in async try/catch + emit events instead of synchronous file writes

### 🚨 Prerendering Breaks Session Pages

Pages using `Astro.session` (`/login`, `/users/*`) must render dynamically:
- Never use `{ prerender: true }` on these routes (session won't exist at build time)
- Static pages can go to SSG output — dynamic SSR during build only for mutation-sensitive pages

### 🚨 File Persistence Gotchas

Current `data/*.json` files have no locks or concurrent write safety:
- Single-process file writes fine (dev environment works this way)
- When migrating to Kafka, replace writes with event emission — backend commits offset = persistence guarantee

## BFF Layer Questions — Senior Checklist Before Designing Events

### Assumptions (Context for Your Team)

1. **SSR Status**: Current Node adapter only for Actions convenience; state ownership = what matters
2. **No BFF yet** → Jump directly to Java REST API is acceptable for MVP
3. **Solo-developer tool** → Minimal concurrency, simple error handling adequate initially
4. **Library data**: PII minimal — user selection without auth is current scope

---

### 1. Alcance y límites: Should We Build a BFF Layer?

- ✅ Assumed: Frontend (Astro) renders only; no mutations or client-side state persistence
- ✅ Assumed: Backend owns game state via Kafka events and persistent storage
- ❓ **Question**: Do we need a NestJS BFF middle layer, or skip to Java REST API directly?
  - *Skip BFF path*: Astro SPA fetches from Java backend; simpler stack but one team
  - *With BFF path*: NestJS aggregates calls, manages auth between frontend and backend

> **Recommendation**: Start with no BFF for MVP. Add NestJS later if you need polyglot persistence or different languages per feature.

---

### 2. Consumidores y carga: Who Is Driving the Data?

- ✅ Assumed: Frontend fetches from Backend via HTTP on user actions
- **Question**: Will this scale to concurrent users (hundreds/monthly DAU) or remain solo community project?
  - Impact: High concurrency → caching layers, rate limiting, load balancing needed
  - Low concurrency → Simple REST endpoints are fine; focus on event idempotency

---

### 3. Datos: What Sensitive Data Needs Protection?

- ✅ Assumed: Game metadata (title, year, platform) public-facing
- ✅ Assumed: Library entries are per-user but not authentication-protected currently
- **Question**: When moving to database layer, what user data should we store in library entries?
  - Answer: Store only `gameId + status` — fetch full game details via separate backend query each render cycle
  - Reduces exposure to accidental leaks

---

### 4. Fallos y resiliencia: What If Backend Unreachable?

- ✅ Assumed: Actions emit Kafka events, eventually consistent with backend
- **Question**: If Java backend is down but Kafka still working, should we serve stale cached responses or show error?
  - UX best practice: Serve stale/cached responses until backend recovers — user sees old state, not error page

---

### 5. Seguridad: What Auth Strategy Fits Your Team?

- ✅ Assumed: Session-based user selection on `/login` without passwords right now
- **Question**: When adding auth layer later, which do you prefer first?
  - Simple HTTP-only session cookies (easiest to start)
  - JWT tokens (standard for microservices/inter-service auth between Astro and backend)

---

### 6. Observabilidad: Metrics vs Console Logs?

- ✅ Assumed: Solo-developer tool initially — console output and simple logging sufficient
- For Java Spring applications, consider adding `/actuator/prometheus` endpoint for Grafana dashboards later if needed

---

### 7. Pruebas: Testing Strategy Target?

- ✅ Assumed: Manual verification during dev (Playwright) + basic unit tests for critical paths
- MVP path: No automated test suite; manual smoke tests after each change
- Growth path: JUnit tests around event handlers and offsets

---

### 8. Compatibilidad: Parallel Migration vs Atomic Switch?

- ✅ Assumed: Gradual migration — don't break existing `data/*.json` immediately
- Safe path: Parallel run both file JSON and Kafka consumers until validated and user data preserved

---

## Default Decisions (If No Specific Requirements Given)

| Area | Default Choice | Why |
|------|----------------|-----|
| BFF Layer | Skip it for MVP, add later if needed | Two-team overhead not worth early-stage simplicity |
| Auth | Simple session cookies first | JWT added when OAuth or inter-service auth required |
| Metrics | Console logs only | Add Prometheus/Grafana when multiple instances run in production |
| Failure Mode | Stale cached responses until backend recovers | Better UX than error screens |
| Data Model | Library entries store minimal PII + fetch full details per request | Reduces exposure to accidental leaks |

---

## Next Steps: Phase-by-Phase Plan (When You're Ready to Build)

### **Phase 0: Current State (File Persistence)**
```bash
npm install && npm run dev
# All Actions go to files in `data/*.json` via `src/lib/db.ts`
```

### **Phase 1: Add Java Backend for Real Persistence**
- Create Spring Boot application with Kafka listener consumers
- Replace `src/actions/` mutations with HTTP handlers that emit events (not write JSON)
- Update `src/lib/db.ts` to read from PostgreSQL or file cache (Kafka offset store)

### **Phase 2: Introduce BFF Layer Between Astro and Backend**
- Build NestJS application (or Node.js Edge Functions) as middle layer
- Frontend fetches from BFF; Java backend still owns state and persistence
- BFF manages auth, aggregates multiple microservice calls if needed

---

## File Structure — Where Things Go

```
src/
├── actions/index.ts        # Mutation handlers (emit Kafka events instead of files)
├── lib/
│   └── db.ts               # Single data layer — migrate to DB reader now
├── layouts/Layout.astro    # Server-rendered layout with session
├── components/             # Reusable UI parts only
└── pages/
    ├── index.astro         # Landing page (session requires dynamic render)
    ├── games/[id].astro    # Game details + add game form (emit event, not direct write)
    └── ...
```

---

## Summary — Why SSR Is Optional Here

- **Current**: Node adapter provides synchronous Actions execution per HTTP request (file persistence convenience)
- **When Java backend owns state**: SSR unnecessary — Astro just renders HTML based on data fetched from wherever (DB, cache, or API)
- **Key takeaway**: Don't let SSR dictate architecture. State ownership (who owns the truth) is what matters, not whether you're using Node adapter

**Recommendation**: Keep SSR for MVP simplicity. When Kafka consumer validates and data integrity verified, decide based on UX needs: serve stale cached responses vs. error pages on backend failure → that choice determines final architecture.
