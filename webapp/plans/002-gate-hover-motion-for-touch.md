# 002 — Gate hover transforms behind hover-capable pointers

- **Status**: TODO
- **Commit**: a270864
- **Severity**: MEDIUM
- **Category**: 6 — Accessibility (ungated `:hover` motion)
- **Estimated scope**: 1 file (`src/styles/global.css`), ~8 lines

## Problem

Both hover lifts apply on touch devices. On phones/tablets, tapping a
**non-navigating** button leaves it stuck in its lifted hover state after
your finger leaves it (browsers keep `:hover` sticky on tap):

- `src/styles/global.css:45-48` — `.pixel-btn:hover { transform:
  translate(-2px, -2px); … }`
- `src/styles/global.css:58-60` — `.card-hover:hover { transform:
  translateY(-3px); }`

Affected controls with no navigation to clear the state: the grid/list view
toggles (`src/pages/juegos/index.astro:24-25`), every status button in
`src/components/StatusSelector.astro:19-27`, the user buttons on
`src/pages/entrar.astro:23`, and QUITAR/AÑADIR buttons on
`src/pages/listas/[id].astro:36,49`.

Playwright's mobile emulation never surfaced this (it doesn't reproduce
sticky `:hover`), which is why the bug survived the repo's documented
375px checks — verify on a real touch device or DevTools device mode.

Card hover lifts themselves are a deliberate, documented decision
(`ANALISIS.md:81`) — only *where they fire* is being fixed, not whether they
exist.

## Target

```css
/* src/styles/global.css — target */
@media (hover: hover) and (pointer: fine) {
  .pixel-btn:hover {
    transform: translate(-2px, -2px);
    box-shadow: 6px 6px 0 0 #000;
  }
}

@media (hover: hover) and (pointer: fine) {
  .card-hover:hover {
    transform: translateY(-3px);
  }
}
```

`.pixel-btn:active` (lines 49–52) and both base/transition rules stay
**outside** the media queries — touch users keep press feedback.

## Repo conventions to follow

- All motion rules live in `src/styles/global.css`; keep each hover rule
  physically next to its `:active`/base rule as today.
- The reduced-motion block (lines 185–191) uses `*, *::before, *::after` and
  needs no change — it keeps working regardless of the new media queries.

## Steps

1. In `src/styles/global.css`, wrap the existing `.pixel-btn:hover` rule
   (lines 45–48) in `@media (hover: hover) and (pointer: fine) { … }`.
2. Wrap the existing `.card-hover:hover` rule (lines 58–60) in its own
   `@media (hover: hover) and (pointer: fine) { … }`.
3. Leave `.pixel-btn` base, `.pixel-btn:active`, `.card-hover` base, and
   their `transition` lines byte-for-byte unchanged.

## Boundaries

- Do NOT gate `:active` — press feedback must fire on touch.
- Do NOT touch Tailwind `hover:` utilities (`hover:text-accent`,
  `hover:bg-panel-light`) — those sit on links that navigate on tap, so
  there is nothing sticky to fix, and reworking them would ripple into
  markup.
- Do NOT change any durations, easings, or translate distances.
- If the code you find no longer matches the excerpts above (drift since
  commit a270864), STOP and report instead of improvising.

## Verification

- **Mechanical**: `npm run build` completes without error.
- **Feel check**:
  - Desktop (mouse): hover a card and a button — lift still eases in/out at
    150ms/120ms, unchanged.
  - DevTools device toolbar (pick a phone model — this switches
    `hover: none`/`pointer: coarse`): tap the ▦/☰ view toggle, a status
    button on a game page, and a user button on `/entrar`. On finger
    release, each button must be **at rest** — no stuck lifted state.
  - Same device mode: press-and-hold a button — `:active` press-down must
    still fire.
  - If a physical phone is available, repeat the tap checks there.
- **Done when**: touch taps never leave a control stuck in hover, and desktop
  hover behavior is visually identical to before.
