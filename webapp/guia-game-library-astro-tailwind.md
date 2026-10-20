# Guía paso a paso: Game Library (Astro + Tailwind CSS)

Guía completa para construir una app de gestión de biblioteca de videojuegos, usando **únicamente Astro + Tailwind CSS**, sin frameworks de UI, sin ORM, sin base de datos externa. Está pensada para que la sigas tú mismo, carpeta por carpeta.

## 0. Qué vamos a construir y por qué así

Funcionalidades pedidas: ver detalle / buscar / añadir videojuegos, cambiar su estado (`wishlist`, `backlog`, `playing`, `beaten`, `paused`, `quit`), crear y gestionar listas, y ver otros usuarios con sus listas.

Para que esto funcione sin añadir un backend aparte, usamos:

- **Astro en modo `server`** (SSR) con el **adaptador Node**, para poder leer/escribir datos y procesar formularios.
- **Astro Actions** (`astro:actions`) para las mutaciones (añadir juego, cambiar estado, crear lista...) — es la forma oficial y más simple de Astro de tener "funciones de backend" sin montar una API REST a mano.
- **Astro Sessions** (incluido en el adaptador Node) para saber "qué usuario soy" sin montar un sistema de login con contraseñas. Es un selector simple de usuario, no autenticación real — suficiente para el alcance pedido.
- **Ficheros JSON como "base de datos"**, leídos/escritos con `node:fs`. No hay motor de BD porque no se ha pedido y añadiría complejidad innecesaria.

Si más adelante quieres una base de datos real o login con contraseña, es un cambio localizado (sustituir `src/lib/db.ts`), no un rediseño.

---

## 1. Prerrequisitos

- Node.js 18.20.8+ o 20.3.0+ (requerido por Astro 5).
- Un editor (VS Code recomendado, con la extensión oficial "Astro").
- Conocimientos básicos de HTML/CSS/JS — no se usa React/Vue/Svelte en ningún momento.

---

## 2. Crear el proyecto

```bash
npm create astro@latest -- game-library
```

Cuando pregunte:
- Template: **Empty**
- TypeScript: **Yes, strict** (opcional, pero ayuda; "strictest" es excesivo aquí)
- Instalar dependencias: **Yes**
- Inicializar git: **Yes**

```bash
cd game-library
```

Estructura inicial relevante:
```
src/
  pages/
  layouts/     (lo crearemos)
  components/  (lo crearemos)
astro.config.mjs
```

---

## 3. Instalar Tailwind CSS 4

Astro integra Tailwind 4 vía el plugin oficial de Vite:

```bash
npx astro add tailwind
```

Esto instala `@tailwindcss/vite`, actualiza `astro.config.mjs` y crea `src/styles/global.css` con:

```css
@import "tailwindcss";
```

Verifica que `astro.config.mjs` tenga:

```js
import { defineConfig } from 'astro/config';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  vite: { plugins: [tailwindcss()] },
});
```

---

## 4. Activar SSR + adaptador Node (necesario para Actions y sesiones)

```bash
npx astro add node
```

Esto añade `@astrojs/node` y actualiza `astro.config.mjs`. Edítalo para que quede así:

```js
import { defineConfig } from 'astro/config';
import tailwindcss from '@tailwindcss/vite';
import node from '@astrojs/node';

export default defineConfig({
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  vite: { plugins: [tailwindcss()] },
});
```

Con `output: 'server'` todas las páginas se renderizan en cada petición por defecto, incluida la home: aunque su contenido es estático, el `Layout` necesita leer `Astro.session` en cada página para saber qué usuario está activo, y eso solo está disponible en páginas renderizadas por servidor. Por eso **no** marcamos la home con `export const prerender = true` — si lo haces, Astro avisa con un warning (`Astro.request.headers was used when rendering a prerendered page`) y el header deja de reflejar el usuario logueado en esa página.

---

## 5. Temática retro/pixel: tipografía y paleta oscura

Añade la fuente pixel en el `<head>` (la pondremos en el layout en el paso 8):

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Press+Start+2P&display=swap" rel="stylesheet">
```

Edita `src/styles/global.css` para definir el tema (Tailwind 4 usa `@theme` en CSS, nada de `tailwind.config.js`):

```css
@import "tailwindcss";

@theme {
  --font-pixel: "Press Start 2P", monospace;

  --color-bg: #0d0b1a;
  --color-panel: #171433;
  --color-panel-light: #211c47;
  --color-border: #3a3570;
  --color-text: #e8e6ff;
  --color-text-dim: #a6a1d9;

  --color-accent: #39ff88;    /* verde arcade */
  --color-accent-2: #ff3fa4;  /* magenta retro */
  --color-accent-3: #3ad8ff;  /* cian */

  --color-wishlist: #8a8fff;
  --color-backlog: #c084fc;
  --color-playing: #3ad8ff;
  --color-beaten: #39ff88;
  --color-paused: #facc15;
  --color-quit: #ff5c5c;
}

body {
  background-color: var(--color-bg);
  color: var(--color-text);
}

/* Utilidad "pixel panel": borde duro, sin antialiasing, sombra escalonada */
.pixel-panel {
  background-color: var(--color-panel);
  border: 4px solid var(--color-border);
  box-shadow: 6px 6px 0 0 #000;
  image-rendering: pixelated;
}

.pixel-btn {
  font-family: var(--font-pixel);
  border: 3px solid #000;
  box-shadow: 4px 4px 0 0 #000;
  transition: transform 0.05s, box-shadow 0.05s;
}
.pixel-btn:active {
  transform: translate(4px, 4px);
  box-shadow: 0 0 0 0 #000;
}
```

Criterio de legibilidad: fondo casi negro (`--color-bg`), texto claro (`--color-text`), acentos de color saturado solo para botones/estados, nunca texto largo en color saturado sobre fondo oscuro saturado (evita vibración visual).

Estas dos clases (`pixel-panel`, `pixel-btn`) son la base sobre la que construimos los componentes `PixelPanel` y `PixelButton` del paso 9 — ahí dejamos de repetirlas a mano en cada página.

---

## 6. Modelar los datos

Crea una carpeta de datos **fuera de `src/`** (para que escribir en ella en dev no dispare recargas de Astro):

```
data/
  games.json
  users.json
  library.json
  lists.json
```

Contenido inicial (`data/games.json`):
```json
[]
```
Igual (`[]`) para `library.json` y `lists.json`.

`data/users.json` (semilla de usuarios de ejemplo, ya que no hay registro):
```json
[
  { "username": "ash", "displayName": "Ash" },
  { "username": "misty", "displayName": "Misty" }
]
```

### Helper de acceso a datos: `src/lib/db.ts`

```ts
import { readFile, writeFile } from 'node:fs/promises';
import { existsSync, mkdirSync } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

const DATA_DIR = path.join(process.cwd(), 'data');

export type GameStatus = 'wishlist' | 'backlog' | 'playing' | 'beaten' | 'paused' | 'quit';

export interface Game {
  id: string;
  title: string;
  platform: string;
  year?: number;
  coverUrl?: string;
  description?: string;
}

export interface LibraryEntry {
  username: string;
  gameId: string;
  status: GameStatus;
  updatedAt: string;
}

export interface GameList {
  id: string;
  username: string;
  name: string;
  description?: string;
  gameIds: string[];
}

export interface User {
  username: string;
  displayName: string;
}

async function readJson<T>(file: string, fallback: T): Promise<T> {
  const filePath = path.join(DATA_DIR, file);
  if (!existsSync(filePath)) return fallback;
  const raw = await readFile(filePath, 'utf-8');
  return raw.trim() ? JSON.parse(raw) : fallback;
}

async function writeJson(file: string, data: unknown) {
  if (!existsSync(DATA_DIR)) mkdirSync(DATA_DIR, { recursive: true });
  await writeFile(path.join(DATA_DIR, file), JSON.stringify(data, null, 2));
}

// --- Users ---
export const getUsers = () => readJson<User[]>('users.json', []);
export const getUser = async (username: string) =>
  (await getUsers()).find((u) => u.username === username) ?? null;

// --- Games ---
export const getGames = () => readJson<Game[]>('games.json', []);

export async function getGame(id: string) {
  return (await getGames()).find((g) => g.id === id) ?? null;
}

export async function searchGames(query: string) {
  const games = await getGames();
  if (!query.trim()) return games;
  const q = query.toLowerCase();
  return games.filter((g) => g.title.toLowerCase().includes(q));
}

export async function addGame(input: Omit<Game, 'id'>) {
  const games = await getGames();
  const existing = games.find(
    (g) => g.title.toLowerCase() === input.title.toLowerCase()
  );
  if (existing) return existing;
  const game: Game = { id: randomUUID(), ...input };
  games.push(game);
  await writeJson('games.json', games);
  return game;
}

// --- Library (estado por usuario) ---
export const getLibrary = () => readJson<LibraryEntry[]>('library.json', []);

export async function getLibraryForUser(username: string) {
  return (await getLibrary()).filter((e) => e.username === username);
}

export async function getStatus(username: string, gameId: string) {
  const entry = (await getLibrary()).find(
    (e) => e.username === username && e.gameId === gameId
  );
  return entry?.status ?? null;
}

export async function setStatus(username: string, gameId: string, status: GameStatus) {
  const library = await getLibrary();
  const entry = library.find((e) => e.username === username && e.gameId === gameId);
  if (entry) {
    entry.status = status;
    entry.updatedAt = new Date().toISOString();
  } else {
    library.push({ username, gameId, status, updatedAt: new Date().toISOString() });
  }
  await writeJson('library.json', library);
}

// --- Lists ---
export const getLists = () => readJson<GameList[]>('lists.json', []);

export async function getListsForUser(username: string) {
  return (await getLists()).filter((l) => l.username === username);
}

export async function getList(id: string) {
  return (await getLists()).find((l) => l.id === id) ?? null;
}

export async function createList(username: string, name: string, description?: string) {
  const lists = await getLists();
  const list: GameList = { id: randomUUID(), username, name, description, gameIds: [] };
  lists.push(list);
  await writeJson('lists.json', lists);
  return list;
}

export async function deleteList(id: string) {
  const lists = (await getLists()).filter((l) => l.id !== id);
  await writeJson('lists.json', lists);
}

export async function addGameToList(listId: string, gameId: string) {
  const lists = await getLists();
  const list = lists.find((l) => l.id === listId);
  if (list && !list.gameIds.includes(gameId)) {
    list.gameIds.push(gameId);
    await writeJson('lists.json', lists);
  }
}

export async function removeGameFromList(listId: string, gameId: string) {
  const lists = await getLists();
  const list = lists.find((l) => l.id === listId);
  if (list) {
    list.gameIds = list.gameIds.filter((id) => id !== gameId);
    await writeJson('lists.json', lists);
  }
}
```

Añade `data/` a `.gitignore` si no quieres versionar los datos de prueba (opcional, depende de si quieres "semilla" compartida en git).

---

## 7. Sesión de usuario (sin login real)

Astro Sessions ya viene activada con el adaptador Node (usa el filesystem para guardarlas). Solo la usamos para recordar "qué usuario soy".

### Página `src/pages/login.astro`

```astro
---
import Layout from '../layouts/Layout.astro';
import PixelButton from '../components/PixelButton.astro';
import PixelPanel from '../components/PixelPanel.astro';
import { getUsers } from '../lib/db';

const users = await getUsers();

if (Astro.request.method === 'POST') {
  const form = await Astro.request.formData();
  const username = form.get('username')?.toString();
  if (username) {
    Astro.session?.set('username', username);
    return Astro.redirect('/');
  }
}
---
<Layout title="Elige tu usuario">
  <PixelPanel class="p-6 max-w-md mx-auto">
    <h1 class="font-pixel text-lg text-accent mb-6">¿Quién eres?</h1>
    <form method="POST" class="flex flex-col gap-3">
      {users.map((u) => (
        <PixelButton name="username" value={u.username} variant="neutral" class="text-left">
          {u.displayName}
        </PixelButton>
      ))}
    </form>
  </PixelPanel>
</Layout>
```

Nota: usar varios botones con el mismo `name="username"` y distinto `value` dentro de un mismo `<form>` es un truco válido en HTML — el navegador manda solo el botón pulsado. `PixelButton` (lo definimos en el paso 9) renderiza un `<button type="submit">` normal cuando no recibe `href`, así que este patrón sigue funcionando igual.

---

## 8. Layout base: `src/layouts/Layout.astro`

```astro
---
import '../styles/global.css';

interface Props {
  title: string;
}
const { title } = Astro.props;
const username = Astro.session ? await Astro.session.get('username') : null;
---
<html lang="es">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>{title} · GameLib</title>
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link
      href="https://fonts.googleapis.com/css2?family=Press+Start+2P&display=swap"
      rel="stylesheet"
    />
  </head>
  <body class="font-sans">
    <header class="border-b-4 border-border bg-panel">
      <nav class="max-w-5xl mx-auto flex items-center justify-between p-4">
        <a href="/" class="font-pixel text-accent text-sm">GameLib</a>
        <div class="flex gap-4 text-sm">
          <a href="/games" class="hover:text-accent">Juegos</a>
          <a href="/lists" class="hover:text-accent">Mis listas</a>
          <a href="/users" class="hover:text-accent">Usuarios</a>
          <a href="/login" class="text-text-dim hover:text-accent">
            {username ? `👤 ${username}` : 'Entrar'}
          </a>
        </div>
      </nav>
    </header>
    <main class="max-w-5xl mx-auto p-4">
      <slot />
    </main>
  </body>
</html>
```

Como usamos `Astro.session`, confirma en `astro.config.mjs` que no hayas desactivado sesiones (por defecto están activas con el adaptador Node).

---

## 9. Componentes reutilizables

Antes de montar el resto de páginas, extraemos a `src/components/` lo que se repetía a mano una y otra vez: el panel con borde pixel, los botones, la pastilla de estado y el selector de estado, y la tarjeta de lista. **La tarjeta de juego (`GameCard`) se deja deliberadamente fuera** — en el catálogo y en el perfil de usuario el marcado de la tarjeta de juego se mantiene igual que estaba, inline en la propia página, sin componente.

### `src/components/PixelPanel.astro`

Envoltorio con el borde duro + sombra escalonada. Usa la directiva `class:list` de Astro para fusionar la clase base con clases extra que le pases desde fuera.

```astro
---
interface Props {
  class?: string;
}
const { class: className = '' } = Astro.props;
---
<div class:list={['pixel-panel', className]}>
  <slot />
</div>
```

### `src/components/PixelButton.astro`

Unifica botones y enlaces con pinta de botón retro. Si recibe `href`, renderiza un `<a>`; si no, un `<button>` (por defecto `type="submit"`, para que sirva tal cual dentro de los `<form>` de las Actions). Acepta `name`/`value` para los casos en que varios botones submiten valores distintos del mismo formulario (selector de estado, selector de usuario).

```astro
---
interface Props {
  href?: string;
  variant?: 'accent' | 'accent-2' | 'accent-3' | 'quit' | 'neutral';
  type?: 'button' | 'submit';
  name?: string;
  value?: string;
  class?: string;
}
const {
  href,
  variant = 'accent',
  type = 'submit',
  name,
  value,
  class: className = '',
} = Astro.props;

const VARIANT_CLASSES: Record<string, string> = {
  accent: 'bg-accent text-black',
  'accent-2': 'bg-accent-2 text-black',
  'accent-3': 'bg-accent-3 text-black',
  quit: 'bg-quit text-black',
  neutral: 'bg-panel-light text-text',
};
---
{href ? (
  <a
    href={href}
    class:list={['pixel-btn font-pixel text-xs px-4 py-2 inline-block', VARIANT_CLASSES[variant], className]}
  >
    <slot />
  </a>
) : (
  <button
    type={type}
    name={name}
    value={value}
    class:list={['pixel-btn font-pixel text-xs px-4 py-2', VARIANT_CLASSES[variant], className]}
  >
    <slot />
  </button>
)}
```

### `src/components/StatusBadge.astro`

Pastilla de color para mostrar un estado, con los colores definidos en el `@theme` del paso 5.

```astro
---
import type { GameStatus } from '../lib/db';

interface Props {
  status: GameStatus;
}
const { status } = Astro.props;

const COLOR_CLASSES: Record<GameStatus, string> = {
  wishlist: 'bg-wishlist',
  backlog: 'bg-backlog',
  playing: 'bg-playing',
  beaten: 'bg-beaten',
  paused: 'bg-paused',
  quit: 'bg-quit',
};
---
<span class:list={['font-pixel text-[10px] px-2 py-1 text-black inline-block', COLOR_CLASSES[status]]}>
  {status.toUpperCase()}
</span>
```

### `src/components/StatusSelector.astro`

El formulario de "elige tu estado para este juego", construido sobre `PixelButton`. Antes vivía inline en el detalle del juego; ahora es un componente con props explícitas.

```astro
---
import { actions } from 'astro:actions';
import PixelButton from './PixelButton.astro';
import type { GameStatus } from '../lib/db';

interface Props {
  username: string;
  gameId: string;
  currentStatus: GameStatus | null;
}
const { username, gameId, currentStatus } = Astro.props;

const STATUSES: GameStatus[] = ['wishlist', 'backlog', 'playing', 'beaten', 'paused', 'quit'];
---
<form method="POST" action={actions.setStatus} class="flex flex-wrap gap-2">
  <input type="hidden" name="username" value={username} />
  <input type="hidden" name="gameId" value={gameId} />
  {STATUSES.map((s) => (
    <PixelButton name="status" value={s} variant={currentStatus === s ? 'accent' : 'neutral'}>
      {s.toUpperCase()}
    </PixelButton>
  ))}
</form>
```

### `src/components/ListCard.astro`

Tarjeta de lista (nombre + número de juegos), usada tanto en "mis listas" como en el perfil público de un usuario.

```astro
---
import type { GameList } from '../lib/db';

interface Props {
  list: GameList;
}
const { list } = Astro.props;
---
<a href={`/lists/${list.id}`} class="pixel-panel p-4 hover:bg-panel-light block">
  <h3 class="font-pixel text-xs text-accent-2 mb-2">{list.name}</h3>
  <p class="text-text-dim text-sm">{list.gameIds.length} juego(s)</p>
</a>
```

---

## 10. Las Actions (mutaciones del backend)

Crea `src/actions/index.ts`:

```ts
import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import * as db from '../lib/db';

export const server = {
  addGame: defineAction({
    accept: 'form',
    input: z.object({
      title: z.string().min(1),
      platform: z.string().min(1),
      year: z.coerce.number().optional(),
      coverUrl: z.string().optional(),
      description: z.string().optional(),
    }),
    handler: async (input) => {
      const game = await db.addGame(input);
      return { id: game.id };
    },
  }),

  setStatus: defineAction({
    accept: 'form',
    input: z.object({
      username: z.string(),
      gameId: z.string(),
      status: z.enum(['wishlist', 'backlog', 'playing', 'beaten', 'paused', 'quit']),
    }),
    handler: async ({ username, gameId, status }) => {
      await db.setStatus(username, gameId, status);
      return { ok: true };
    },
  }),

  createList: defineAction({
    accept: 'form',
    input: z.object({
      username: z.string(),
      name: z.string().min(1),
      description: z.string().optional(),
    }),
    handler: async ({ username, name, description }) => {
      const list = await db.createList(username, name, description);
      return { id: list.id };
    },
  }),

  deleteList: defineAction({
    accept: 'form',
    input: z.object({ listId: z.string() }),
    handler: async ({ listId }) => {
      await db.deleteList(listId);
      return { ok: true };
    },
  }),

  addGameToList: defineAction({
    accept: 'form',
    input: z.object({ listId: z.string(), gameId: z.string() }),
    handler: async ({ listId, gameId }) => {
      await db.addGameToList(listId, gameId);
      return { ok: true };
    },
  }),

  removeGameFromList: defineAction({
    accept: 'form',
    input: z.object({ listId: z.string(), gameId: z.string() }),
    handler: async ({ listId, gameId }) => {
      await db.removeGameFromList(listId, gameId);
      return { ok: true };
    },
  }),
};
```

Todas estas acciones se llaman desde formularios HTML normales (`<form method="POST" action={actions.xxx}>`), sin JavaScript de cliente — Astro se encarga del resto.

---

## 11. Home page: landing con hero + 2 secciones

`src/pages/index.astro`:

```astro
---
import Layout from '../layouts/Layout.astro';
import PixelPanel from '../components/PixelPanel.astro';
import PixelButton from '../components/PixelButton.astro';
---
<Layout title="Inicio">
  <!-- HERO -->
  <PixelPanel class="text-center py-16 px-6 my-8">
    <h1 class="font-pixel text-2xl md:text-4xl text-accent mb-4 leading-relaxed">
      GAME LIBRARY
    </h1>
    <p class="text-text-dim max-w-xl mx-auto mb-8">
      Tu biblioteca de videojuegos, a tu manera. Guarda lo que juegas,
      lo que quieres jugar, y lo que ya terminaste.
    </p>
    <PixelButton href="/games" class="px-6 py-3">VER JUEGOS</PixelButton>
  </PixelPanel>

  <!-- SECCIÓN 1: QUÉ ES -->
  <section class="grid md:grid-cols-2 gap-6 my-12">
    <PixelPanel class="p-6">
      <h2 class="font-pixel text-accent-3 text-sm mb-3">¿QUÉ ES GAMELIB?</h2>
      <p class="text-text-dim">
        Una app pensada para gamers que quieren llevar el control de su
        catálogo: qué tienen en el radar (<em>wishlist</em>), qué está
        pendiente (<em>backlog</em>), qué están jugando ahora, qué ya
        terminaron y qué dejaron a medias.
      </p>
    </PixelPanel>
    <PixelPanel class="p-6">
      <h2 class="font-pixel text-accent-2 text-sm mb-3">LISTAS Y COMUNIDAD</h2>
      <p class="text-text-dim">
        Crea tus propias listas temáticas ("Mis platinos", "Para jugar en
        verano"...) y echa un vistazo a las listas de otros usuarios para
        descubrir nuevos juegos.
      </p>
    </PixelPanel>
  </section>

  <!-- SECCIÓN 2: POR QUÉ USARLA -->
  <PixelPanel class="p-6 my-12">
    <h2 class="font-pixel text-accent text-sm mb-4">¿POR QUÉ USARLA?</h2>
    <ul class="grid md:grid-cols-3 gap-4 text-text-dim">
      <li class="pixel-panel bg-panel-light p-4">
        <strong class="text-accent-3">Simple.</strong>
        Sin cuentas complicadas, sin anuncios, sin fricción.
      </li>
      <li class="pixel-panel bg-panel-light p-4">
        <strong class="text-accent-2">Organizado.</strong>
        Seis estados claros para cada juego de tu biblioteca.
      </li>
      <li class="pixel-panel bg-panel-light p-4">
        <strong class="text-beaten">Social.</strong>
        Explora las listas y biblioteca de otros gamers.
      </li>
    </ul>
  </PixelPanel>
</Layout>
```

---

## 12. Catálogo + búsqueda: `src/pages/games/index.astro`

`GameCard` no existe como componente a propósito — la tarjeta de juego se queda igual que en la primera versión de la guía, inline en la propia página.

```astro
---
import Layout from '../../layouts/Layout.astro';
import PixelPanel from '../../components/PixelPanel.astro';
import PixelButton from '../../components/PixelButton.astro';
import { searchGames } from '../../lib/db';

const q = Astro.url.searchParams.get('q') ?? '';
const games = await searchGames(q);
---
<Layout title="Juegos">
  <form method="GET" class="flex gap-2 mb-6">
    <input
      type="text"
      name="q"
      value={q}
      placeholder="Buscar videojuego..."
      class="pixel-panel bg-panel-light flex-1 px-3 py-2 text-sm outline-none"
    />
    <PixelButton type="submit" variant="accent-3">BUSCAR</PixelButton>
  </form>

  {games.length === 0 && (
    <PixelPanel class="p-6 text-center">
      <p class="text-text-dim mb-4">No hay resultados para "{q}".</p>
      <PixelButton href={`/games/nuevo?title=${encodeURIComponent(q)}`} variant="accent-2">
        AÑADIR "{q || 'NUEVO JUEGO'}"
      </PixelButton>
    </PixelPanel>
  )}

  <div class="grid md:grid-cols-3 gap-4">
    {games.map((g) => (
      <a href={`/games/${g.id}`} class="pixel-panel p-4 hover:bg-panel-light block">
        <h3 class="font-pixel text-xs text-accent mb-2">{g.title}</h3>
        <p class="text-text-dim text-sm">{g.platform} {g.year ? `· ${g.year}` : ''}</p>
      </a>
    ))}
  </div>

  <PixelButton href="/games/nuevo" class="mt-6">+ AÑADIR JUEGO</PixelButton>
</Layout>
```

---

## 13. Añadir videojuego: `src/pages/games/nuevo.astro`

```astro
---
import Layout from '../../layouts/Layout.astro';
import PixelPanel from '../../components/PixelPanel.astro';
import PixelButton from '../../components/PixelButton.astro';
import { actions } from 'astro:actions';

const prefillTitle = Astro.url.searchParams.get('title') ?? '';
const result = Astro.getActionResult(actions.addGame);
if (result && !result.error) {
  return Astro.redirect(`/games/${result.data.id}`);
}
---
<Layout title="Añadir juego">
  <PixelPanel class="p-6 max-w-lg">
    <form method="POST" action={actions.addGame} class="flex flex-col gap-4">
      <h1 class="font-pixel text-accent text-sm mb-2">AÑADIR JUEGO</h1>

      <label class="text-sm text-text-dim">
        Título
        <input name="title" required value={prefillTitle}
          class="pixel-panel bg-panel-light w-full px-3 py-2 mt-1" />
      </label>

      <label class="text-sm text-text-dim">
        Plataforma
        <input name="platform" required
          class="pixel-panel bg-panel-light w-full px-3 py-2 mt-1" />
      </label>

      <label class="text-sm text-text-dim">
        Año
        <input name="year" type="number"
          class="pixel-panel bg-panel-light w-full px-3 py-2 mt-1" />
      </label>

      <label class="text-sm text-text-dim">
        Descripción
        <textarea name="description" rows="3"
          class="pixel-panel bg-panel-light w-full px-3 py-2 mt-1"></textarea>
      </label>

      <PixelButton type="submit" class="self-start">GUARDAR</PixelButton>

      {result?.error && <p class="text-quit">Revisa los datos del formulario.</p>}
    </form>
  </PixelPanel>
</Layout>
```

Importante: para usar `Astro.getActionResult`, la página **no puede** estar prerenderizada (debe ser SSR, que es el default en todo lo que no marques con `prerender = true`).

---

## 14. Detalle de juego + cambio de estado: `src/pages/games/[id].astro`

```astro
---
import Layout from '../../layouts/Layout.astro';
import PixelPanel from '../../components/PixelPanel.astro';
import PixelButton from '../../components/PixelButton.astro';
import StatusSelector from '../../components/StatusSelector.astro';
import { actions } from 'astro:actions';
import { getGame, getStatus, getListsForUser } from '../../lib/db';

const { id } = Astro.params;
const game = await getGame(id!);
if (!game) return Astro.redirect('/games');

const username = Astro.session ? await Astro.session.get('username') : null;
const currentStatus = username ? await getStatus(username, game.id) : null;
const myLists = username ? await getListsForUser(username) : [];
---
<Layout title={game.title}>
  <PixelPanel class="p-6 max-w-2xl">
    <h1 class="font-pixel text-accent text-base mb-2">{game.title}</h1>
    <p class="text-text-dim mb-4">{game.platform} {game.year ? `· ${game.year}` : ''}</p>
    {game.description && <p class="mb-6">{game.description}</p>}

    {username ? (
      <>
        <div class="mb-6">
          <StatusSelector username={username} gameId={game.id} currentStatus={currentStatus} />
        </div>

        {myLists.length > 0 && (
          <form method="POST" action={actions.addGameToList} class="flex gap-2">
            <input type="hidden" name="gameId" value={game.id} />
            <select name="listId" class="pixel-panel bg-panel-light px-3 py-2 text-sm">
              {myLists.map((l) => <option value={l.id}>{l.name}</option>)}
            </select>
            <PixelButton type="submit" variant="accent-3">AÑADIR A LISTA</PixelButton>
          </form>
        )}
      </>
    ) : (
      <p class="text-text-dim">
        <a href="/login" class="text-accent-3 underline">Elige un usuario</a> para marcar
        estado o añadir a una lista.
      </p>
    )}
  </PixelPanel>
</Layout>
```

---

## 15. Listas: `src/pages/lists/index.astro` y `src/pages/lists/[id].astro`

**`src/pages/lists/index.astro`** (mis listas + crear):

```astro
---
import Layout from '../../layouts/Layout.astro';
import PixelPanel from '../../components/PixelPanel.astro';
import PixelButton from '../../components/PixelButton.astro';
import ListCard from '../../components/ListCard.astro';
import { actions } from 'astro:actions';
import { getListsForUser } from '../../lib/db';

const username = Astro.session ? await Astro.session.get('username') : null;
if (!username) return Astro.redirect('/login');

const lists = await getListsForUser(username);
---
<Layout title="Mis listas">
  <PixelPanel class="p-4 mb-6">
    <form method="POST" action={actions.createList} class="flex gap-2">
      <input type="hidden" name="username" value={username} />
      <input name="name" required placeholder="Nombre de la lista"
        class="pixel-panel bg-panel-light flex-1 px-3 py-2 text-sm" />
      <PixelButton type="submit">CREAR</PixelButton>
    </form>
  </PixelPanel>

  <div class="grid md:grid-cols-3 gap-4">
    {lists.map((l) => <ListCard list={l} />)}
  </div>
</Layout>
```

**`src/pages/lists/[id].astro`** (gestionar juegos dentro de la lista):

```astro
---
import Layout from '../../layouts/Layout.astro';
import PixelPanel from '../../components/PixelPanel.astro';
import PixelButton from '../../components/PixelButton.astro';
import { actions } from 'astro:actions';
import { getList, getGames } from '../../lib/db';

const { id } = Astro.params;
const list = await getList(id!);
if (!list) return Astro.redirect('/lists');

const allGames = await getGames();
const gamesInList = allGames.filter((g) => list.gameIds.includes(g.id));
const gamesNotInList = allGames.filter((g) => !list.gameIds.includes(g.id));
---
<Layout title={list.name}>
  <PixelPanel class="p-6 mb-6">
    <h1 class="font-pixel text-accent-2 text-base mb-2">{list.name}</h1>
    {list.description && <p class="text-text-dim mb-4">{list.description}</p>}

    <form method="POST" action={actions.deleteList}>
      <input type="hidden" name="listId" value={list.id} />
      <PixelButton type="submit" variant="quit">ELIMINAR LISTA</PixelButton>
    </form>
  </PixelPanel>

  <div class="grid md:grid-cols-2 gap-6">
    <div>
      <h2 class="font-pixel text-xs text-accent mb-3">EN LA LISTA</h2>
      {gamesInList.map((g) => (
        <form method="POST" action={actions.removeGameFromList}
          class="pixel-panel p-3 flex justify-between items-center mb-2">
          <input type="hidden" name="listId" value={list.id} />
          <input type="hidden" name="gameId" value={g.id} />
          <a href={`/games/${g.id}`} class="hover:text-accent">{g.title}</a>
          <PixelButton type="submit" variant="quit" class="px-2 py-1">QUITAR</PixelButton>
        </form>
      ))}
    </div>

    <div>
      <h2 class="font-pixel text-xs text-text-dim mb-3">AÑADIR OTROS JUEGOS</h2>
      {gamesNotInList.map((g) => (
        <form method="POST" action={actions.addGameToList}
          class="pixel-panel p-3 flex justify-between items-center mb-2">
          <input type="hidden" name="listId" value={list.id} />
          <input type="hidden" name="gameId" value={g.id} />
          <span>{g.title}</span>
          <PixelButton type="submit" class="px-2 py-1">AÑADIR</PixelButton>
        </form>
      ))}
    </div>
  </div>
</Layout>
```

---

## 16. Ver otros usuarios y sus listas

**`src/pages/users/index.astro`** (sin cambios — las tarjetas de usuario son tan simples que no justifican un componente):

```astro
---
import Layout from '../../layouts/Layout.astro';
import { getUsers } from '../../lib/db';

const users = await getUsers();
---
<Layout title="Usuarios">
  <div class="grid md:grid-cols-3 gap-4">
    {users.map((u) => (
      <a href={`/users/${u.username}`} class="pixel-panel p-4 hover:bg-panel-light block">
        <h3 class="font-pixel text-xs text-accent">{u.displayName}</h3>
      </a>
    ))}
  </div>
</Layout>
```

**`src/pages/users/[username].astro`** (perfil público: biblioteca por estado + sus listas). Los enlaces a cada juego dentro de cada bloque de estado se mantienen como enlaces simples, igual que antes — no son `GameCard`, así que no cambian.

```astro
---
import Layout from '../../layouts/Layout.astro';
import PixelPanel from '../../components/PixelPanel.astro';
import StatusBadge from '../../components/StatusBadge.astro';
import ListCard from '../../components/ListCard.astro';
import { getUser, getLibraryForUser, getListsForUser, getGames } from '../../lib/db';

const { username } = Astro.params;
const user = await getUser(username!);
if (!user) return Astro.redirect('/users');

const [library, lists, games] = await Promise.all([
  getLibraryForUser(username!),
  getListsForUser(username!),
  getGames(),
]);

const gameById = new Map(games.map((g) => [g.id, g]));
const STATUSES = ['wishlist', 'backlog', 'playing', 'beaten', 'paused', 'quit'] as const;
---
<Layout title={user.displayName}>
  <h1 class="font-pixel text-accent text-base mb-6">{user.displayName}</h1>

  <div class="grid md:grid-cols-2 gap-4 mb-10">
    {STATUSES.map((status) => {
      const entries = library.filter((e) => e.status === status);
      return (
        <PixelPanel class="p-4">
          <div class="flex items-center gap-2 mb-2">
            <StatusBadge status={status} />
            <span class="text-text-dim text-sm">({entries.length})</span>
          </div>
          <ul class="text-sm text-text-dim">
            {entries.map((e) => {
              const g = gameById.get(e.gameId);
              return g && <li><a href={`/games/${g.id}`} class="hover:text-accent">{g.title}</a></li>;
            })}
          </ul>
        </PixelPanel>
      );
    })}
  </div>

  <h2 class="font-pixel text-accent-2 text-sm mb-4">LISTAS</h2>
  <div class="grid md:grid-cols-3 gap-4">
    {lists.map((l) => <ListCard list={l} />)}
  </div>
</Layout>
```

---

## 17. Probar la app en local

```bash
npm run dev
```

Flujo de prueba recomendado:
1. Entra en `/login` y elige "Ash".
2. Ve a `/games`, busca algo que no exista → te ofrece añadirlo → añádelo.
3. Entra al detalle del juego → cambia su estado a `playing`.
4. Ve a `/lists`, crea una lista, añade el juego a esa lista desde su detalle o desde la propia lista.
5. Entra en `/login` otra vez y cambia a "Misty".
6. Ve a `/users/ash` y confirma que ves el juego de Ash en el estado correcto y en su lista — así validas la parte de "ver otros usuarios y sus listas".

---

## 18. Build y despliegue

```bash
npm run build
npm run start   # levanta el servidor Node standalone (adaptador en modo standalone)
```

Esto genera un servidor Node real (porque hay SSR + Actions + sesiones), no un sitio puramente estático. Para desplegarlo necesitas un host que ejecute un proceso Node persistente (VM, contenedor, Render, Railway, etc.) y un disco persistente para la carpeta `data/` — en plataformas serverless/edge esta aproximación de "JSON en disco" no sirve, sería el primer punto a cambiar.

---

## 19. Mejoras de experiencia: animaciones, color, tipografía y vista cuadrícula/lista

Pulido añadido después de la primera versión funcional, probado en un navegador real. En el camino aparecieron dos bugs reales que vale la pena conocer si sigues este patrón en tu propio proyecto.

### Paleta menos negra + tipografía secundaria a juego con la de los títulos

`src/styles/global.css` — se aclara el fondo (ya no es casi negro) y se sustituye la fuente del cuerpo (antes el `font-sans` por defecto de Tailwind) por una pixel más legible que combina con "Press Start 2P":

```css
@theme {
  --font-pixel: "Press Start 2P", monospace;
  --font-sans: "Pixelify Sans", ui-sans-serif, system-ui, sans-serif;

  --color-bg: #1a1733;        /* antes #0d0b1a — ya no es prácticamente negro */
  --color-panel: #221f45;
  --color-panel-light: #2b2759;
  --color-border: #4a4580;
  /* ...el resto de colores de estado no cambia */
}
```

En `src/layouts/Layout.astro`, añade la segunda familia a la URL de Google Fonts:

```html
<link
  href="https://fonts.googleapis.com/css2?family=Press+Start+2P&family=Pixelify+Sans:wght@400..700&display=swap"
  rel="stylesheet"
/>
```

Al sobrescribir la variable `--font-sans` de Tailwind (en vez de crear una variable nueva), no hace falta tocar ningún `class="font-sans"` existente — sigue el mismo patrón que ya usa Astro para [registrar fuentes personalizadas con Tailwind](https://docs.astro.build/en/guides/fonts/#register-fonts-in-tailwind).

### Animaciones sutiles: botones, tarjetas, fondo del hero y cambios de página

Todo en `global.css`, sin tocar las plantillas (salvo una clase `hero-bg` en la home y `card-hover` en las tarjetas clicables):

```css
.pixel-btn {
  font-family: var(--font-pixel);
  border: 3px solid #000;
  box-shadow: 4px 4px 0 0 #000;
  transition: transform 0.12s ease-out, box-shadow 0.12s ease-out;
}
.pixel-btn:hover {
  transform: translate(-2px, -2px);
  box-shadow: 6px 6px 0 0 #000;
}
.pixel-btn:active {
  transform: translate(2px, 2px);
  box-shadow: 2px 2px 0 0 #000;
}

/* Lift sutil para tarjetas clicables (juego, lista, usuario) */
.card-hover {
  transition: transform 0.15s ease-out, box-shadow 0.15s ease-out;
}
.card-hover:hover {
  transform: translateY(-3px);
}

/* Patrón de fondo animado, sutil, solo para el hero de la landing */
.hero-bg {
  position: relative;
  z-index: 0;      /* fuerza un contexto de apilamiento propio para el ::before */
  overflow: hidden;
}
.hero-bg::before {
  content: "";
  position: absolute;
  inset: -20%;
  z-index: -1;
  background-image:
    repeating-linear-gradient(0deg, rgba(58, 216, 255, 0.28) 0 2px, transparent 2px 24px),
    repeating-linear-gradient(90deg, rgba(255, 63, 164, 0.22) 0 2px, transparent 2px 24px);
  animation: hero-grid-drift 16s linear infinite;
}
@keyframes hero-grid-drift {
  from { transform: translate(0, 0); }
  to { transform: translate(24px, 24px); }
}

/* Respeta la preferencia de movimiento reducido para TODAS las animaciones de la app */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

Aplica `class="hero-bg ..."` al `PixelPanel` del hero en `src/pages/index.astro`, y `card-hover` junto al resto de clases en las tarjetas de `src/pages/games/index.astro`, `src/components/ListCard.astro` y `src/pages/users/index.astro`.

Para transiciones suaves **entre páginas** (no solo dentro de una página), Astro trae de serie el componente `<ClientRouter />`, que añade un crossfade automático a toda navegación por enlaces (`<a>`), respeta `prefers-reduced-motion` sin configuración extra, y no afecta a los `<form>` de las Actions (solo intercepta clics en enlaces y navegación de historial, así que el flujo de `setStatus`/`createList`/etc. sigue haciendo una navegación normal). Se añade una vez en `src/layouts/Layout.astro`:

```astro
---
import '../styles/global.css';
import { ClientRouter } from 'astro:transitions';
// ...
---
<html lang="es">
  <head>
    <!-- ... -->
    <ClientRouter />
  </head>
  <!-- ... -->
</html>
```

### Centrar el contenido verticalmente

Con `output: 'server'`, páginas con poco contenido (como `/login`) quedaban pegadas arriba, con un hueco grande debajo. Se centra con flexbox usando márgenes automáticos — **no** con `justify-content: center` directamente, porque esa técnica recorta el contenido por arriba si en algún momento es más alto que la pantalla; con `margin: auto` los márgenes simplemente se reducen a `0` y el contenido vuelve a fluir normal desde arriba:

```astro
<body class="font-sans min-h-screen flex flex-col">
  <header>...</header>
  <main class="max-w-5xl mx-auto w-full flex-1 flex flex-col p-4">
    <div class="my-auto w-full">
      <slot />
    </div>
  </main>
</body>
```

### Bug real encontrado: la home no puede ser `prerender = true`

En la primera versión, `src/pages/index.astro` tenía `export const prerender = true` por ser una página sin datos. Pero el `Layout` llama a `Astro.session` en **todas** las páginas para saber qué usuario está activo, y eso requiere la petición HTTP real — no existe en build time. Resultado: un warning (`Astro.request.headers was used when rendering a prerendered page`) y el header nunca mostraba el usuario logueado en la home. Se soluciona quitando esa línea; el coste de no prerenderizar una página tan ligera es insignificante.

### Vista cuadrícula / lista en el catálogo

En `src/pages/games/index.astro`, dos botones alternan la clase del contenedor de resultados (persistiendo la elección en `localStorage`) en vez de duplicar el marcado:

```astro
<div class="flex gap-1" role="group" aria-label="Vista de la lista de juegos">
  <PixelButton type="button" id="view-grid-btn" variant="neutral" class="px-3 py-2">▦</PixelButton>
  <PixelButton type="button" id="view-list-btn" variant="neutral" class="px-3 py-2">☰</PixelButton>
</div>

<div id="games-container" class="grid md:grid-cols-3 gap-4">
  {games.map((g) => (
    <a
      href={`/games/${g.id}`}
      class="pixel-panel card-hover p-4 hover:bg-panel-light flex items-center justify-between gap-4"
    >
      <h3 class="font-pixel text-xs text-accent">{g.title}</h3>
      <p class="text-text-dim text-sm whitespace-nowrap">{g.platform} {g.year ? `· ${g.year}` : ''}</p>
    </a>
  ))}
</div>

<script>
  document.addEventListener('astro:page-load', () => {
    const container = document.getElementById('games-container');
    const gridBtn = document.getElementById('view-grid-btn');
    const listBtn = document.getElementById('view-list-btn');
    if (!container || !gridBtn || !listBtn) return;

    const GRID_CLASSES = ['grid', 'md:grid-cols-3', 'gap-4'];
    const LIST_CLASSES = ['flex', 'flex-col', 'gap-3'];
    const STORAGE_KEY = 'gamelib-games-view';

    function applyView(view: string) {
      container.classList.remove(...GRID_CLASSES, ...LIST_CLASSES);
      container.classList.add(...(view === 'list' ? LIST_CLASSES : GRID_CLASSES));
      for (const [btn, active] of [
        [gridBtn, view !== 'list'],
        [listBtn, view === 'list'],
      ] as const) {
        btn.classList.toggle('bg-accent', active);
        btn.classList.toggle('text-black', active);
        btn.classList.toggle('bg-panel-light', !active);
        btn.setAttribute('aria-pressed', String(active));
      }
      localStorage.setItem(STORAGE_KEY, view);
    }

    applyView(localStorage.getItem(STORAGE_KEY) ?? 'grid');
    gridBtn.addEventListener('click', () => applyView('grid'));
    listBtn.addEventListener('click', () => applyView('list'));
  });
</script>
```

Importante: el truco solo funciona porque cada tarjeta usa el **mismo** layout interno (`flex items-center justify-between`, título a la izquierda y plataforma/año a la derecha) tanto en cuadrícula como en lista — así basta con cambiar las clases del contenedor (`grid` ↔ `flex flex-col`) sin tocar cada tarjeta. Se escucha `astro:page-load` en vez de ejecutar el script directamente porque, con `<ClientRouter />` activo, es el evento que dispara tanto en la carga inicial como después de cada navegación cliente — un script suelto en la raíz del archivo solo se ejecutaría la primera vez.

### Bug real encontrado: `PixelButton` no reenviaba `id`

Al poner `id="view-grid-btn"` en un `<PixelButton>`, el botón no aparecía con ese `id` en el HTML final — Astro **no** pasa automáticamente los atributos no declarados en la interfaz `Props` de un componente al elemento que renderiza (el mismo motivo por el que `class` necesita tratamiento especial, visto en el paso 9). Arreglo en `src/components/PixelButton.astro`: capturar el resto de props y reenviarlas.

```astro
---
interface Props {
  href?: string;
  variant?: 'accent' | 'accent-2' | 'accent-3' | 'quit' | 'neutral';
  type?: 'button' | 'submit';
  name?: string;
  value?: string;
  class?: string;
  [attr: string]: unknown; // permite id, aria-*, data-*, etc.
}
const { href, variant = 'accent', type = 'submit', name, value, class: className = '', ...rest } = Astro.props;
---
{href ? (
  <a href={href} class:list={[/* ... */]} {...rest}>
    <slot />
  </a>
) : (
  <button type={type} name={name} value={value} class:list={[/* ... */]} {...rest}>
    <slot />
  </button>
)}
```

### Bug real encontrado: paneles con `max-w-*` pero sin `mx-auto` no quedan centrados

En `src/pages/games/[id].astro` y `src/pages/games/nuevo.astro`, el `PixelPanel` tenía `class="p-6 max-w-2xl"` (o `max-w-lg`) pero sin `mx-auto`. `max-w-*` solo limita el ancho; sin un margen automático a los lados, un `<div>` de bloque sigue pegado a la izquierda del `<main>` (que ya está centrado en la página, pero es más ancho — `max-w-5xl` — que el panel interior). La regla a recordar: **todo contenedor con `max-w-*` que no deba ocupar el ancho completo necesita también `mx-auto`.**

```astro
<PixelPanel class="p-6 max-w-2xl mx-auto">
```

### Carátulas placeholder

Como todavía no hay carátulas reales, se añade un placeholder con el patrón de "textura ausente" (el clásico tablero de ajedrez de los motores de videojuegos cuando falta un asset — temáticamente perfecto para esta app) en vez de un hueco vacío o un color plano.

`src/styles/global.css`:

```css
.cover-placeholder {
  background-image: conic-gradient(
    var(--color-panel-light) 0deg 90deg,
    var(--color-panel) 90deg 180deg,
    var(--color-panel-light) 180deg 270deg,
    var(--color-panel) 270deg 360deg
  );
  background-size: 20px 20px;
}
```

`src/components/CoverPlaceholder.astro` — nuevo componente, reutilizado en el detalle (carátula grande) y en el catálogo (miniatura), con la inicial del título como contenido:

```astro
---
interface Props {
  title: string;
  class?: string;
}
const { title, class: className = '' } = Astro.props;
const initial = title.trim().charAt(0).toUpperCase() || '?';
---
<div
  class:list={['pixel-panel cover-placeholder flex items-center justify-center overflow-hidden', className]}
  role="img"
  aria-label={`Carátula pendiente para ${title}`}
>
  <span class="font-pixel text-accent bg-bg/70 px-2 py-1">{initial}</span>
</div>
```

En el detalle (`src/pages/games/[id].astro`), antes del título, con fallback a una imagen real si `game.coverUrl` existe:

```astro
{game.coverUrl ? (
  <img src={game.coverUrl} alt={`Carátula de ${game.title}`} class="w-full aspect-video object-cover mb-4 pixel-panel" />
) : (
  <CoverPlaceholder title={game.title} class="w-full aspect-video mb-4 text-5xl" />
)}
```

En el catálogo (`src/pages/games/index.astro`), como miniatura dentro de cada tarjeta — el resto de la tarjeta (título a la derecha, con `flex-1`) no cambia, solo se añade la miniatura delante:

```astro
<a href={`/games/${g.id}`} class="pixel-panel card-hover p-4 hover:bg-panel-light flex items-center gap-4">
  {g.coverUrl ? (
    <img src={g.coverUrl} alt="" class="w-12 h-12 shrink-0 object-cover pixel-panel" />
  ) : (
    <CoverPlaceholder title={g.title} class="w-12 h-12 shrink-0 text-lg" />
  )}
  <div class="flex items-center justify-between flex-1 gap-4">
    <h3 class="font-pixel text-xs text-accent">{g.title}</h3>
    <p class="text-text-dim text-sm whitespace-nowrap">{g.platform} {g.year ? `· ${g.year}` : ''}</p>
  </div>
</a>
```

El tamaño de la miniatura/carátula se controla solo con la clase que le pasa cada página (`w-12 h-12` en el catálogo, `w-full aspect-video` en el detalle) — el componente no asume ningún tamaño propio.

---

## 20. Hero a pantalla completa, más "chicha" en el fondo, responsive y botón de volver

### Hero a 100% de la altura real de pantalla (correcto en móvil)

`100vh` en móvil es tramposo: no tiene en cuenta que la barra de direcciones del navegador aparece/desaparece, así que el hero puede quedar más alto que la pantalla visible o dar saltos al hacer scroll. La unidad correcta es `dvh` (*dynamic viewport height*), que sí lo tiene en cuenta — se usa como mejora progresiva sobre `vh` (los navegadores que no conocen `dvh` simplemente ignoran esa línea y se quedan con la anterior):

```css
/* src/styles/global.css */
.hero-viewport {
  min-height: calc(100vh - 4rem); /* fallback */
  min-height: calc(100dvh - 4rem); /* 4rem = alto fijo de la cabecera */
}
```

Para que el `4rem` del cálculo sea exacto (y no una aproximación), la cabecera pasa de una altura "la que le salga" por el padding, a una altura fija y explícita:

```astro
<!-- src/layouts/Layout.astro -->
<header class="border-b-4 border-border bg-panel h-16 shrink-0">
  <nav class="max-w-5xl mx-auto h-full flex items-center justify-between gap-2 px-3 sm:px-4">
    <!-- ... -->
  </nav>
</header>
```

Comprobado con Playwright en tres tamaños de pantalla distintos: la altura del hero es exactamente `viewport - 64px` en los tres casos, sin aproximaciones.

### El hero ocupa todo el ancho de pantalla (full-bleed)

El resto de la app vive dentro de `<main class="max-w-5xl mx-auto ...">`, pero el hero necesita salirse de esa caja para llegar a los bordes de la pantalla. El truco clásico de CSS para "escapar" de un contenedor centrado sin tocar su estructura:

```css
.full-bleed {
  width: 100vw;
  margin-left: calc(50% - 50vw);
  margin-right: calc(50% - 50vw);
}
```

`src/pages/index.astro` — el hero deja de ser un `PixelPanel` con borde (quedaba raro a pantalla completa) y pasa a ser una sección a ancho y alto completos, con el fondo animado detrás:

```astro
<section class="full-bleed hero-viewport hero-bg flex flex-col items-center justify-center text-center px-6">
  <h1 class="font-pixel text-3xl sm:text-4xl md:text-5xl text-accent mb-6 leading-relaxed">
    GAME LIBRARY
  </h1>
  <p class="text-text-dim max-w-xl mb-8 text-sm sm:text-base">
    Tu biblioteca de videojuegos, a tu manera. Guarda lo que juegas,
    lo que quieres jugar, y lo que ya terminaste.
  </p>
  <PixelButton href="/games" class="px-6 py-3">VER JUEGOS</PixelButton>
</section>
```

### Más "chicha" en el fondo: aurora de color + rejilla pixel

A la rejilla animada que ya había (`.hero-bg::before`) se le añade una segunda capa detrás (`::after`): tres manchas de color (verde, magenta, cian) muy difuminadas que se mueven despacio, como una aurora. El resultado combina el toque retro de la rejilla con algo de profundidad y movimiento de color — mucho más vivo que la rejilla sola:

```css
.hero-bg::after {
  content: "";
  position: absolute;
  inset: -30%;
  z-index: -2; /* detrás de la rejilla (-1) y del contenido (auto) */
  background-image:
    radial-gradient(circle at 25% 30%, rgba(57, 255, 136, 0.45), transparent 55%),
    radial-gradient(circle at 75% 65%, rgba(255, 63, 164, 0.4), transparent 55%),
    radial-gradient(circle at 50% 85%, rgba(58, 216, 255, 0.35), transparent 60%);
  filter: blur(50px);
  animation: hero-aurora-drift 14s ease-in-out infinite alternate;
}
@keyframes hero-aurora-drift {
  0% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(6%, -6%) scale(1.15); }
  100% { transform: translate(-6%, 5%) scale(0.95); }
}
```

Sigue respetando `prefers-reduced-motion` porque usa `animation`, cubierto por la regla global del paso 19.

### Bug real encontrado: la fila de búsqueda se solapaba en móvil

En `/games`, el buscador + botón "BUSCAR" + los dos botones de vista cuadrícula/lista intentaban caber los cuatro en una sola fila incluso en pantallas de 375px. El contenedor tenía `flex-wrap`, pero como el buscador iba dentro de su propio `<form class="flex-1 min-w-48">`, ese `min-w-48` (192px) competía con los botones de vista por el ancho disponible en vez de ceder el paso — resultado: el botón "BUSCAR" se renderizaba solapado con los botones de vista, cortado visualmente. Comprobado con Playwright midiendo anchos reales de cada elemento (no a simple vista), no era un wrap mal calculado sino un overflow silencioso.

Arreglo: apilar en vertical en móvil y poner en fila solo a partir de `sm:`:

```astro
<div class="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-2 mb-6">
  <form method="GET" class="flex gap-2 sm:flex-1 sm:min-w-48">
    <!-- input + botón BUSCAR -->
  </form>
  <div class="flex gap-1" role="group" aria-label="Vista de la lista de juegos">
    <!-- botones de vista -->
  </div>
</div>
```

### Responsive: cabecera, grids y botones

- La cabecera reduce tamaño de letra y espaciado en móvil (`text-xs sm:text-sm`, `gap-2 sm:gap-4`), y el grupo de enlaces tiene `overflow-x-auto` como red de seguridad si algún día se añade otro enlace y no cupiera.
- Los grids de tarjetas (`grid md:grid-cols-3`, `grid md:grid-cols-2`) ya eran responsive desde el principio: sin el prefijo `md:` caen a una sola columna en pantallas pequeñas por defecto.
- Probado con Playwright en tres anchos de pantalla (375px móvil, 768px tablet, 1280px escritorio) en todas las páginas, incluyendo con sesión iniciada (selector de estado, formularios de listas): cero overflow horizontal, cero errores de consola.

### Botón de volver atrás

Vive en la cabecera (`src/layouts/Layout.astro`), a la izquierda del logo, y solo se muestra si no estás en la home (no tendría sentido ahí):

```astro
---
const isHome = Astro.url.pathname === '/';
---
{!isHome && (
  <PixelButton type="button" id="back-btn" variant="neutral" class="px-2 py-2" aria-label="Volver atrás">
    ←
  </PixelButton>
)}
```

```html
<script>
  document.addEventListener('astro:page-load', () => {
    document.getElementById('back-btn')?.addEventListener('click', () => history.back());
  });
</script>
```

Usa `history.back()` (navegación real del navegador), no una ruta fija — así siempre vuelve a donde estabas realmente, y sigue funcionando con `<ClientRouter />` activo porque el router de Astro escucha los eventos de navegación del historial del navegador.

### Legibilidad del subtítulo del hero + un pulso de brillo sutil

Con el fondo animado detrás (rejilla + aurora de color), el subtítulo en `text-text-dim` (un lila apagado) perdía contraste en los momentos en que una zona clara de la aurora pasaba justo detrás del texto. Dos cambios en `src/styles/global.css`:

1. Una sombra oscura fija (`text-shadow`) en el título y el subtítulo, que actúa como un halo que garantiza contraste **sea lo que sea** lo que haya detrás en ese instante — es la misma técnica que se usa en cualquier texto sobre una foto o vídeo de fondo.
2. Un pulso de brillo cian muy sutil, solo en el subtítulo, para que no quede estático (como pedía): la sombra de base no cambia, solo se le añade un resplandor que crece y decrece despacio.

```css
.hero-title {
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.7), 0 0 4px rgba(0, 0, 0, 0.8);
}
.hero-subtitle {
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.9), 0 0 3px rgba(0, 0, 0, 0.95);
  animation: hero-subtitle-glow 4s ease-in-out infinite;
}
@keyframes hero-subtitle-glow {
  0%, 100% {
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.9), 0 0 3px rgba(0, 0, 0, 0.95), 0 0 8px rgba(58, 216, 255, 0.15);
  }
  50% {
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.9), 0 0 3px rgba(0, 0, 0, 0.95), 0 0 16px rgba(58, 216, 255, 0.55);
  }
}
```

En `src/pages/index.astro`, el subtítulo pasa de `text-text-dim` (apagado) a `text-text` (el claro casi blanco) + la clase `hero-subtitle`, y el `<h1>` gana la clase `hero-title`. Comprobado con capturas en dos momentos distintos de la animación de fondo: el texto se mantiene legible en ambos.

### Subtítulo más grande y un barrido de luz en el fondo del hero (no en el texto)

Dos ajustes independientes:

**Tamaño del subtítulo** — de `text-sm sm:text-base` a `text-base sm:text-lg md:text-xl` en `src/pages/index.astro`.

**Barrido de luz en el fondo** (lo que realmente se pedía al decir "anímalo también" — no el texto, el fondo del hero): un tercer elemento decorativo, además de la rejilla (`::before`) y la aurora (`::after`) que ya había. Como un elemento solo puede tener dos pseudo-elementos, este es un `<div>` real dentro de la sección del hero:

```astro
<!-- src/pages/index.astro -->
<section class="full-bleed hero-viewport hero-bg flex flex-col items-center justify-center text-center px-6">
  <div class="hero-scanline" aria-hidden="true"></div>
  <!-- ...título, subtítulo, botón... -->
</section>
```

```css
/* src/styles/global.css */
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

Primera versión del barrido con tono verde/cian (a juego con la paleta) resultó casi invisible: se mezclaba con los mismos colores de la aurora que ya había detrás. Se cambió a un brillo blanco con un pico de opacidad marcado en el centro de la banda — así contrasta igual sin importar qué zona de color de la aurora esté cruzando en ese momento. Comprobado con capturas espaciadas 1.5s: el barrido se desplaza de arriba a abajo en bucle, de forma mucho más evidente que la rejilla/aurora (que son deliberadamente ambientales y lentas).

---

## 21. Posibles mejoras futuras (fuera de este alcance)

- Sustituir `src/lib/db.ts` por una base de datos real (SQLite con `better-sqlite3`, o Turso/Postgres) si quieres concurrencia y despliegue serverless.
- Login real con contraseña, en vez del selector de usuario.
- Imágenes de carátula automáticas vía una API externa de videojuegos.
- Paginación en `/games` cuando el catálogo crezca.
- Extraer `GameCard` como componente si en algún momento la tarjeta de juego empieza a divergir entre páginas (hoy se mantiene inline a propósito).
- Animar también la transición entre vista cuadrícula y lista (hoy el cambio es instantáneo; animar un reflow de `grid` a `flex` requeriría una técnica FLIP, que se dejó fuera por complejidad frente al beneficio).

Con esto tienes la app completa funcionando solo con Astro + Tailwind: landing con hero animado y dos secciones, catálogo con búsqueda, alta de juegos y vista cuadrícula/lista, cambio de estado, gestión de listas, y perfiles públicos de usuario con su biblioteca y listas — todo en estética retro/pixel con fondo oscuro (no negro) y texto legible, con transiciones suaves entre páginas, y con los componentes repetidos (`PixelPanel`, `PixelButton`, `StatusBadge`, `StatusSelector`, `ListCard`) ya extraídos a `src/components/`.
