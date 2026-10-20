# 005 — Sync the card's background color with its lift

- **Status**: TODO
- **Commit**: a270864
- **Severity**: LOW
- **Category**: 2 — Easing & duration / 7 — Cohesion (mismatched pair in one gesture)
- **Estimated scope**: 1 file (`src/styles/global.css`), 1 line

## Problem

On hover, a card's transform eases in over 150ms while its background tint
teleports instantly — two properties of the same gesture running at different
speeds:

```css
/* src/styles/global.css:55-60 — current */
.card-hover {
  transition: transform 0.15s ease-out, box-shadow 0.15s ease-out;
}
.card-hover:hover {
  transform: translateY(-3px);
}
```

The tint comes from Tailwind's `hover:bg-panel-light` on the same elements
(`src/components/ListCard.astro:9`, `src/pages/juegos/index.astro:43`,
`src/pages/usuarios/index.astro:10`). `background-color` is not in the
transition's property list, so it applies at frame 0 while the lift is still
traveling — the color leads and the motion follows.

## Target

```css
/* target */
.card-hover {
  transition:
    transform 0.15s ease-out,
    box-shadow 0.15s ease-out,
    background-color 0.15s ease-out;
}
.card-hover:hover {
  transform: translateY(-3px);
}
```

Same duration and easing as the existing pair so lift, shadow, and tint start
and land together.

## Repo conventions to follow

- Transitions are declared with the CSS shorthand and keyword easings in
  `src/styles/global.css` — mirror `.pixel-btn` (line 43), which lists its
  properties explicitly. Never use `transition: all`.
- The deliberate values (150ms, `ease-out`) are documented in
  `guia-game-library-astro-tailwind.md` §19 — reuse them, do not retune.

## Steps

1. In `src/styles/global.css` line 56, append `, background-color 0.15s
   ease-out` to `.card-hover`'s transition property list.

## Boundaries

- Do NOT touch `.pixel-btn` (its `box-shadow`/`transform` pair is correct)
  or nav links' `hover:text-accent` (instant color there is crisp on
  purpose — it is not part of a moving gesture).
- Do NOT change durations or easings.
- Do NOT edit markup or Tailwind utility classes.
- If the code you find no longer matches the excerpt above (drift since
  commit a270864), STOP and report instead of improvising.

## Verification

- **Mechanical**: `npm run build` completes without error.
- **Feel check**: on `/juegos`, hover a game card and watch at DevTools →
  Animations panel 10% speed:
  - The `bg-panel-light` tint and the −3px lift must begin and complete
    **together** (before: tint was instant, lift trailed it).
  - Un-hovering, both return together.
  - A nav link in the header (`hover:text-accent`) must still snap instantly
    — untouched.
- **Done when**: slow-motion playback shows tint and lift in lockstep, and
  everything else about the hover is unchanged.
