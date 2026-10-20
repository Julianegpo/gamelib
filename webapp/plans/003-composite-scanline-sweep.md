# 003 — Make the hero scanline sweep composite-only (transform, not background-position)

- **Status**: TODO
- **Commit**: a270864
- **Severity**: MEDIUM
- **Category**: 5 — Performance (animating a paint property)
- **Estimated scope**: 1 file (`src/styles/global.css`), ~25 lines rewritten

## Problem

The CRT-style scanline sweep animates `background-position` across a
full-viewport element — a repaint every frame instead of a GPU-composited
transform, running **forever** on the landing page:

```css
/* src/styles/global.css:125-146 — current */
.hero-scanline {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background-image: linear-gradient(
    to bottom,
    transparent,
    rgba(255, 255, 255, 0.3) 46%,
    rgba(255, 255, 255, 0.65) 50%,
    rgba(255, 255, 255, 0.3) 54%,
    transparent
  );
  background-size: 100% 50%;
  background-repeat: no-repeat;
  background-position: 0 -60%;
  animation: hero-scan-sweep 6s linear infinite;
}
@keyframes hero-scan-sweep {
  from { background-position: 0 -60%; }
  to { background-position: 0 160%; }
}
```

Only `transform` and `opacity` animate on the compositor; `background-position`
forces the browser to re-paint the region each frame for the whole 6s loop.

## Target

Move the gradient onto a band element (`.hero-scanline::before`, height 50%
of the container = exactly the current `background-size: 100% 50%` image) and
sweep it with `transform`. The percentages are **visually identical** to the
old background-position math: `background-position: P%` offsets the image by
`P% × (container − image)` = `P% × 50%` of container height, while
`translateY(P%)` moves the band by `P% ×` its own height (also 50% of
container). So −60% → −30% container and 160% → 80% container — the same
travel. `overflow: hidden` replaces the implicit background clipping.

```css
/* target — src/styles/global.css */
.hero-scanline {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  overflow: hidden;
}
.hero-scanline::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 50%;
  background-image: linear-gradient(
    to bottom,
    transparent,
    rgba(255, 255, 255, 0.3) 46%,
    rgba(255, 255, 255, 0.65) 50%,
    rgba(255, 255, 255, 0.3) 54%,
    transparent
  );
  animation: hero-scan-sweep 6s linear infinite;
}
@keyframes hero-scan-sweep {
  from { transform: translateY(-60%); }
  to { transform: translateY(160%); }
}
```

## Repo conventions to follow

- Keyframes and hero ambient styles all live in `src/styles/global.css`
  (same file, same section — keep the Spanish comment above
  `.hero-scanline` updated if desired).
- The existing reduced-motion rule targets `*, *::before, *::after`
  (`src/styles/global.css:185-191`), so the new `::before` band is already
  covered — with `animation-duration: 0.01ms; iteration-count: 1` it lands on
  `translateY(160%)` (clipped below the fold), matching today's reduced-motion
  end state (`background-position: 0 160%` = also below the fold).

## Steps

1. In `src/styles/global.css`, replace the `.hero-scanline` rule (lines
   125–142): remove `background-image`, `background-size`,
   `background-repeat`, `background-position`, and the `animation` line from
   the container; add `overflow: hidden;`.
2. Add the `.hero-scanline::before` rule with the band's positioning
   (`position: absolute; left: 0; top: 0; width: 100%; height: 50%`), the
   **exact** gradient from the current code (do not retune the stops), and
   `animation: hero-scan-sweep 6s linear infinite;`.
3. Replace the `@keyframes hero-scan-sweep` body with the `transform`
   versions shown above (same keyframe name, same 6s duration).

## Boundaries

- Do NOT change gradient stops, opacity values, duration, or timing function.
- Do NOT touch `.hero-bg::before` (grid drift) or `.hero-bg::after` (aurora).
- Do NOT add `will-change` — CSS animations on `transform` composite without
  it, and it would permanently reserve a layer.
- Do NOT change the markup in `src/pages/index.astro` (`.hero-scanline` is
  already an empty `aria-hidden` div — the `::before` is enough).
- If the code you find no longer matches the excerpt above (drift since
  commit a270864), STOP and report instead of improvising.

## Verification

- **Mechanical**: `npm run build` completes without error.
- **Feel check**:
  - Before editing, note how the sweep looks (or capture a 3s-in
    screenshot): a soft white band crossing the hero top→bottom over 6s,
    clipped at the hero edges.
  - After editing, the sweep must be **indistinguishable**: same band width,
    same gradient falloff, starts above the hero at 0s, fully below at 6s,
    no visible band while outside the box (that is the `overflow: hidden`).
  - DevTools → Rendering → **Paint flashing**: before the change, the hero
    repaints (green) continuously during the sweep; after, the running sweep
    must show **no recurring green** over the hero.
  - Animations panel: slow `hero-scan-sweep` to 10% and confirm the band
    moves smoothly with no jitter.
  - `prefers-reduced-motion: reduce` → sweep frozen out of view, hero static.
- **Done when**: paint-flashing shows no per-frame repaint during the sweep
  and a side-by-side screenshot at the 3s mark looks the same as before the
  change.
