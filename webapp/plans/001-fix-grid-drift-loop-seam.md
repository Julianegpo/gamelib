# 001 — Fix the 4px loop seam in the hero grid drift

- **Status**: TODO
- **Commit**: a270864
- **Severity**: MEDIUM
- **Category**: 4 — Interruptibility (loop seam)
- **Estimated scope**: 1 file (`src/styles/global.css`), 2 numbers

## Problem

The hero's drifting pixel grid snaps backwards 4px every 16 seconds when its
loop restarts. The background pattern repeats every **24px**, but the keyframe
translates by **28px** — 28 mod 24 = 4, so each loop iteration ends 4px past
the pattern period and the reset is visible as a hop.

```css
/* src/styles/global.css:97-101 — current */
background-image:
  repeating-linear-gradient(0deg, rgba(58, 216, 255, 0.28) 0 2px, transparent 2px 24px),
  repeating-linear-gradient(90deg, rgba(255, 63, 164, 0.22) 0 2px, transparent 2px 24px);
animation: hero-grid-drift 16s linear infinite;
```

```css
/* src/styles/global.css:114-116 — current */
@keyframes hero-grid-drift {
  from { transform: translate(0, 0); }
  to { transform: translate(28px, 28px); }
}
```

Both gradients have a 24px period (x-period from the 90deg one, y-period from
the 0deg one), so the translation must be a whole multiple of 24px on **both**
axes for the wrap to be invisible.

## Target

```css
/* target */
@keyframes hero-grid-drift {
  from { transform: translate(0, 0); }
  to { transform: translate(24px, 24px); }
}
```

24px/16s = 1.5px/s — the drift speed stays effectively identical to the
current 1.75px/s; only the seam disappears. `translate(48px, 48px)` is also
seamless if a slightly faster drift is ever wanted, but use **24px** here to
preserve current speed.

## Repo conventions to follow

- All keyframes live together in `src/styles/global.css` (lines 114–163);
  follow the existing `from { } to { }` style used by `hero-grid-drift`.
- Hero ambient loops are deliberately `linear` when motion is constant — do
  not change the timing function.

## Steps

1. In `src/styles/global.css`, in `@keyframes hero-grid-drift` (lines
   114–116), replace `translate(28px, 28px)` with `translate(24px, 24px)`.
2. Nothing else changes — same keyframe name, same `16s linear infinite`
   animation on `.hero-bg::before`.

## Boundaries

- Do NOT touch `hero-aurora-drift`, `hero-scan-sweep`, or `hero-subtitle-glow`.
- Do NOT change the animation duration or timing function.
- Do NOT edit the `background-image` gradients (their 24px period is the
  reference value this fix depends on).
- If the code you find no longer matches the excerpt above (drift since commit
  a270864), STOP and report instead of improvising.

## Verification

- **Mechanical**: `npm run build` completes without error.
- **Feel check**: open the landing page and watch the grid for **at least two
  full loops (32s)**. In DevTools → Animations panel, slow `hero-grid-drift`
  to 10% and step across the loop boundary (0% ⇄ 100%):
  - Grid lines must glide continuously — **no backward hop** at the restart.
  - The drift must still be perceptible (it is deliberately very slow).
  - Rendering panel → `prefers-reduced-motion: reduce` → background is static
    (global rule already handles this; confirm it still does).
- **Done when**: two consecutive loop restarts show no visible jump at 100%
  playback, and none at 10% slow motion.
