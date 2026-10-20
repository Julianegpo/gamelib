# 006 — First-visit-only hero entrance (staggered fade-up)

- **Status**: TODO
- **Commit**: a270864
- **Severity**: LOW
- **Category**: 8 — Missed opportunities (additive; rare/first-time delight)
- **Estimated scope**: 2 files (`src/pages/index.astro`, `src/styles/global.css`), ~30 lines
- **Depends on**: plan 004 (provides the `--ease-out` token in `src/styles/global.css`)

## Problem

The landing hero — the app's documented delight spot (`ANALISIS.md:81`:
animations are "más vistosas solo en el hero porque es la primera
impresión") — has continuous ambient loops but **no entrance**. On a
first-ever visit the title, subtitle, and CTA simply exist. A staggered
fade-up is exactly the rare, first-time moment the delight budget is for.

Hard frequency constraint: it must play **once per browser session**. The
hero re-renders on every return to `/` (the app uses `<ClientRouter />`), so
an ungated entrance would replay constantly and fail the frequency gate.

Current hero markup:

```astro
<!-- src/pages/index.astro:7-17 — current -->
<section class="full-bleed hero-viewport hero-bg flex flex-col items-center justify-center text-center px-6">
  <div class="hero-scanline" aria-hidden="true"></div>
  <h1 class="hero-title font-pixel text-3xl sm:text-4xl md:text-5xl text-accent mb-6 leading-relaxed">
    GAME LIBRARY
  </h1>
  <p class="hero-subtitle text-text max-w-xl mb-8 text-base sm:text-lg md:text-xl">
    Tu biblioteca de videojuegos, a tu manera. …
  </p>
  <PixelButton href="/juegos" class="px-6 py-3">VER JUEGOS</PixelButton>
</section>
```

Existing styles that constrain the implementation:

```css
/* src/styles/global.css:149-155 — current */
.hero-title {
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.7), 0 0 4px rgba(0, 0, 0, 0.8);
}
.hero-subtitle {
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.9), 0 0 3px rgba(0, 0, 0, 0.95);
  animation: hero-subtitle-glow 4s ease-in-out infinite;
}
```

Two traps this design avoids:
1. `.hero-subtitle` already owns an `animation` (the glow) — the entrance
   must be **composed** into the same shorthand, not overwrite it.
2. The CTA is a `.pixel-btn` whose `:active`/`:hover` use `transition:
   transform` — an entrance animation with `fill-mode: forwards` would keep
   owning `transform` forever and **kill the button's press feedback**.
   `fill-mode: backwards` solves this: after the animation finishes, the
   element returns to its base style, leaving transforms to transitions.

## Target

**1. Keyframes and rules** — `src/styles/global.css`, right after the
existing `.hero-title` / `.hero-subtitle` rules (keep their `text-shadow`
lines untouched):

```css
@keyframes hero-fade-up {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

/* First visit (no .hero-seen on <html>): staggered entrance */
.hero-title {
  animation: hero-fade-up 300ms var(--ease-out) backwards;
}
.hero-subtitle {
  animation:
    hero-subtitle-glow 4s ease-in-out infinite,
    hero-fade-up 300ms var(--ease-out) 60ms backwards;
}
.hero-cta {
  animation: hero-fade-up 300ms var(--ease-out) 120ms backwards;
}

/* Repeat visits: kill only the entrance, keep the ambient glow */
html.hero-seen .hero-title,
html.hero-seen .hero-cta {
  animation: none;
}
html.hero-seen .hero-subtitle {
  animation: hero-subtitle-glow 4s ease-in-out infinite;
}
```

Values, all from the audit playbook: duration **300ms** (entrances stay
under 300ms), easing **`var(--ease-out)`** = `cubic-bezier(0.23, 1, 0.32, 1)`
(introduced by plan 004), stagger **0 / 60 / 120ms** (30–80ms per step),
translation **`translateY(12px)` → 0** with opacity 0 → 1 (never `scale(0)`;
`backwards` holds the hidden start state during the stagger delays).

**2. Session gate** — `src/pages/index.astro`, as the **first element**
inside the Layout slot (i.e. immediately after the opening `<Layout
title="Inicio">`, before the hero `<section>`). Being an inline script
placed early in the body, it executes synchronously during parsing and
blocks first paint — the class lands before the hero is ever painted, so
neither visit type flashes:

```astro
<script is:inline data-astro-rerun>
  try {
    if (sessionStorage.getItem('gamelib-hero-seen')) {
      document.documentElement.classList.add('hero-seen');
    } else {
      sessionStorage.setItem('gamelib-hero-seen', '1');
    }
  } catch {
    /* almacenamiento no disponible: la animación se repetirá; inofensivo */
  }
</script>
```

`data-astro-rerun` guarantees the inline script re-executes after client-side
navigation (the class must be re-applied if `<html>` attributes were swapped);
it is idempotent. `sessionStorage` is per-tab — a fresh tab gets the
entrance once, which is the intended "first visit" semantics (same pattern as
the repo's `gamelib-games-view` key).

**3. Tag the CTA** — `src/pages/index.astro` line 16:

```astro
<PixelButton href="/juegos" class="px-6 py-3 hero-cta">VER JUEGOS</PixelButton>
```

(`PixelButton` forwards `class` to the element — `src/components/PixelButton.astro:19,49`.)

## Repo conventions to follow

- All keyframes and animation rules live in `src/styles/global.css`.
- `sessionStorage` keys are prefixed `gamelib-` (see
  `src/pages/juegos/index.astro:90`).
- Scripts bind via lifecycle events under `<ClientRouter />`; inline +
  `data-astro-rerun` is the documented Astro mechanism for re-running inline
  scripts after navigation.
- The single global reduced-motion rule (`src/styles/global.css:185-191`)
  already collapses this entrance to 0.01ms/1 iteration — content appears
  instantly for reduced-motion users, movement dropped. Do not add a second
  reduced-motion block.

## Steps

1. Confirm `--ease-out` exists in the `@theme` block of
   `src/styles/global.css` (plan 004). **If it is absent, add
   `--ease-out: cubic-bezier(0.23, 1, 0.32, 1);` first** — this plan cannot
   proceed without it.
2. Add `@keyframes hero-fade-up` and the five rules from the Target section
   to `src/styles/global.css`, preserving the existing `.hero-title` /
   `.hero-subtitle` `text-shadow` declarations (edit those rules in place by
   appending `animation:` where specified, and add the `html.hero-seen`
   overrides after them).
3. In `src/pages/index.astro`, insert the gated inline `<script>` as the
   first element after `<Layout title="Inicio">`.
4. Add the `hero-cta` class to the VER JUEGOS `<PixelButton>` (line 16).
5. Run the feel checks below.

## Boundaries

- Only the three hero elements (title, subtitle, CTA) get the entrance —
  do NOT animate the info panels below the fold.
- Do NOT remove or alter `hero-subtitle-glow`, the hero background loops, or
  any `text-shadow`.
- Do NOT use `localStorage` (would replay nothing in a new tab and
  everything forever across sessions — the spec is per-session).
- Do NOT add `fill-mode: forwards` anywhere in this plan (it would break the
  CTA's press/hover transform transitions).
- Do NOT touch `Layout.astro` or `PixelButton.astro`.
- If the code you find no longer matches the excerpts above (drift since
  commit a270864), STOP and report instead of improvising.

## Verification

- **Mechanical**: `npm run build` completes without error.
- **Feel check** (use a fresh tab each time sessionStorage matters):
  - First visit to `/`: title fades up first, subtitle 60ms behind, CTA
    60ms behind that — all done in ~420ms total, landing softly (strong
    ease-out: fast start, gentle settle). In Animations panel at 10%,
    confirm each element starts at `opacity: 0 / translateY(12px)` and
    nothing pops.
  - Navigate away (Juegos) and back: **no replay** — hero is immediately
    static except the ambient loops and subtitle glow.
  - Reload `/`: no replay. Open a **new tab** and visit `/`: entrance plays
    once there (per-tab session — correct).
  - On a first visit, hover and press the VER JUEGOS button **after** the
    entrance finishes: hover lift and press feedback must work normally
    (the `backwards` fill-mode guarantee — if they don't, `forwards` leaked
    in; STOP and fix).
  - Rendering panel → `prefers-reduced-motion: reduce` → fresh session: hero
    content appears instantly (global rule), no 12px slide.
  - Soft-navigation edge: with the entrance already seen, navigate to `/`
    via the GameLib logo — confirm no flash of hidden hero content (the
    inline script must apply `hero-seen` before the hero paints).
- **Done when**: the entrance plays exactly once per tab session with the
  0/60/120ms stagger, never replays, never blocks the CTA's press feedback,
  and reduced-motion users see instant content.
