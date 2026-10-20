# 004 — Pin the page crossfade (150ms, strong ease-out) and correct the guide's wrong claim about forms

- **Status**: TODO
- **Commit**: a270864
- **Severity**: MEDIUM
- **Category**: 4 — Interruptibility / 7 — Cohesion & tokens
- **Estimated scope**: 3 files (`src/styles/global.css`, `src/layouts/Layout.astro`, `guia-game-library-astro-tailwind.md`), ~45 lines

## Problem

Every navigation in the app runs Astro's **untuned default** crossfade — and
so does every form submission. The repo's guide explicitly documents the
opposite:

```markdown
<!-- guia-game-library-astro-tailwind.md:1198 — current (key sentence) -->
… añade un crossfade automático a toda navegación por enlaces (`<a>`), respeta
`prefers-reduced-motion` sin configuración extra, y **no afecta a los `<form>`
de las Actions (solo intercepta clics en enlaces y navegación de historial,
así que el flujo de `setStatus`/`createList`/etc. sigue haciendo una
navegación normal)**.
```

That has been false since Astro 4: the `<ClientRouter />` intercepts `<form>`
GET **and** POST submissions (Astro docs → View transitions →
"Transitions with forms": https://docs.astro.build/en/guides/view-transitions/#transitions-with-forms).
The practical effect: every status change, list edit, login pick, and search
ghost-fades the whole page using whatever defaults Astro ships — a
double-exposed crossfade nobody chose, on the app's most frequent mutation
path, while the codebase believes those actions navigate plainly.

`ANALISIS.md:81` documents the *deliberate* choice: subtle crossfades between
pages. That decision stands — what was never a decision is the **tuning**
(duration/easing) and the form behavior. This plan keeps the crossfade
(option a), pins it explicitly, and fixes the documentation.

Current entry point:

```astro
<!-- src/layouts/Layout.astro:3,13,24 — current -->
import { ClientRouter } from 'astro:transitions';
…
<html lang="es">
…
    <ClientRouter />
```

## Target

**1. Motion token** — `src/styles/global.css`, inside the `@theme`
block: `--ease-out: cubic-bezier(0.23, 1, 0.32, 1);` — **add it**. The
crossfade below consumes it.

**2. Crossfade keyframes** — `src/styles/global.css`, after the existing
hero keyframes (keep the repo's `from/to` style):

```css
@keyframes page-fade-out {
  from { opacity: 1; }
  to { opacity: 0; }
}
@keyframes page-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
```

**3. Transition object + page default** — `src/layouts/Layout.astro`:

```astro
---
import '../styles/global.css';
import { ClientRouter } from 'astro:transitions';
import PixelButton from '../components/PixelButton.astro';

interface Props {
  title: string;
}
const { title } = Astro.props;
const username = Astro.session ? await Astro.session.get('username') : null;
const isHome = Astro.url.pathname === '/';

const crossfade = {
  forwards: {
    old: { name: 'page-fade-out', duration: '0.15s', easing: 'var(--ease-out)', fillMode: 'both' },
    new: { name: 'page-fade-in', duration: '0.15s', easing: 'var(--ease-out)', fillMode: 'both' },
  },
  backwards: {
    old: { name: 'page-fade-out', duration: '0.15s', easing: 'var(--ease-out)', fillMode: 'both' },
    new: { name: 'page-fade-in', duration: '0.15s', easing: 'var(--ease-out)', fillMode: 'both' },
  },
};
---
<html lang="es" transition:animate={crossfade}>
```

Rationale for the values (from the audit playbook, never approximated):
duration **0.15s** — navigation is the app's highest-frequency motion, UI
animations stay under 300ms, and this beats Astro's ~0.2s default; easing
**`cubic-bezier(0.23, 1, 0.32, 1)`** — both the exiting old page and the
entering new page should start fast (`ease-out` covers entering *and*
exiting), and built-in `ease` is too weak for deliberate motion. Identical
pair on `forwards` and `backwards` keeps back-navigation symmetric (this is a
fade, not a directional slide).

**4. Guide correction** — `guia-game-library-astro-tailwind.md`, in the
paragraph at line 1198, replace the bolded key sentence above with:

> … añade un crossfade automático a toda navegación por enlaces (`<a>`) y —
> desde Astro 4 — también a los `<form>` con GET y POST, así que el flujo de
> `setStatus`/`createList`/etc. recibe el mismo crossfade. La app lo mantiene
> a propósito (coherente con el crossfade sutil de `ANALISIS.md`) y lo afina
> explícitamente a 150 ms con curva fuerte de salida en `Layout.astro`.
> Respeta `prefers-reduced-motion` sin configuración extra.

Keep the rest of the paragraph (the `<ClientRouter />` import snippet that
follows) unchanged.

## Repo conventions to follow

- Design tokens live in the `@theme` block of `src/styles/global.css`
  (`--font-pixel`, `--color-accent`, …) — `--ease-out` joins them in step 1;
  do not add a second token.
- Keyframes live in the lower section of that same file (the `hero-*` and
  `page-*` keyframes sit together); match their `from/to` style.
- Comments in this codebase are Spanish — write the guide correction and any
  new inline comment in Spanish if you add one.
- Astro's `<ClientRouter />` already auto-disables its transitions under
  `prefers-reduced-motion` (Astro docs → View transitions → Accessibility) —
  no reduced-motion work is needed for this plan.

## Steps

1. Add `--ease-out: cubic-bezier(0.23, 1, 0.32, 1);` to the `@theme`
   block of `src/styles/global.css` — the crossfade below consumes it.
2. Append `@keyframes page-fade-out` and `@keyframes page-fade-in` after the
   existing hero keyframes.
3. In `src/layouts/Layout.astro`, define `crossfade` in frontmatter exactly
   as shown and change `<html lang="es">` to
   `<html lang="es" transition:animate={crossfade}>`.
4. Edit the paragraph at `guia-game-library-astro-tailwind.md:1198` per the
   replacement text above.
5. Run the feel check — **only if** text ghosting (two pages legible at once
   during the swap) is clearly visible at 10% slow motion, apply the
   playbook's crossfade mask by adding blur to both keyframes:

   ```css
   @keyframes page-fade-out {
     from { opacity: 1; filter: blur(0); }
     to { opacity: 0; filter: blur(2px); }
   }
   @keyframes page-fade-in {
     from { opacity: 0; filter: blur(2px); }
     to { opacity: 1; filter: blur(0); }
   }
   ```

   Blur stays at 2px (well under the 20px ceiling) and only exists for the
   150ms transition. If the crossfade reads cleanly without it, do NOT add
   the blur.

## Boundaries

- Keep the crossfade on ALL navigations including form mutations — switching
  to `data-astro-reload` (option b) was explicitly rejected; do not add it.
- Do NOT set `transition:animate` on individual elements — `<html>` is the
  page default for everything.
- Do NOT change the `fallback` prop on `<ClientRouter />`.
- Do NOT retune the hero keyframes or any hover/press transition.
- Other docs (`ANALISIS.md`, `README.md`, `AGENTS.md`) are accurate on this
  point — do not edit them.
- If the code you find no longer matches the excerpts above (drift since
  commit a270864), STOP and report instead of improvising.

## Verification

- **Mechanical**: `npm run build` completes without error; the guide
  paragraph no longer claims forms are unaffected.
- **Feel check**:
  - Navigate forward (nav links) and backward (the ← button): the swap must
    finish in ~150ms with a fast start and soft landing — noticeably snappier
    than before, never sluggish at the start.
  - Submit the search form on `/juegos`, click a status button on a game
    page, and submit `ELIMINAR`'s sibling actions on a list page: each
    mutation crossfades the page the same 150ms (this is the intended,
    now-documented behavior).
  - DevTools → Animations panel → slow the `page-fade-in`/`page-fade-out`
    entries to 10%: check whether both pages' text is legible simultaneously
    (double exposure). If yes → apply step 5's blur mask and re-check; if
    no → leave keyframes clean.
  - Check `--ease-out` resolved: DevTools → Computed on the running
    animation must show `cubic-bezier(0.23, 1, 0.32, 1)`, confirming the
    `@theme` token reached the stylesheet (if it shows an empty/invalid
    easing, the token was stripped — STOP and report).
  - Rendering panel → `prefers-reduced-motion: reduce` → navigation swaps
    with no animation at all (Astro's built-in guard).
  - **Scroll observation (report only)**: after a mutation on a long page
    (e.g. add/remove games on `/listas/[id]`), note whether scroll position
    is preserved or jumps to top. Do not fix it here — record the behavior
    in the plan's PR/diff description as a potential follow-up.
- **Done when**: all four flows (link nav, back nav, search submit, status
  mutation) visibly run the pinned 150ms crossfade, the computed easing is
  the token value, the guide text matches reality, and the build passes.
