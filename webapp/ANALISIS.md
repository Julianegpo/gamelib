# Análisis del proyecto — Game Library

Estado del proyecto a fecha de este documento: funcional, probado en navegador real (escritorio y móvil), sin commits todavía (todo en working tree).

## 1. Qué es

Aplicación de gestión de biblioteca de videojuegos: catálogo con búsqueda y alta de juegos, seguimiento de estado por usuario (`wishlist`, `backlog`, `playing`, `beaten`, `paused`, `quit`), listas personalizadas, y perfiles públicos de usuario con su biblioteca y sus listas. Estética retro/pixel, tema oscuro.

## 2. Stack y por qué

| Pieza | Elección | Motivo |
|---|---|---|
| Framework | Astro 7 (`output: 'server'`) | Pedido explícito: solo Astro + Tailwind, sin frameworks de UI (React/Vue/etc.) |
| Estilos | Tailwind CSS 4 (`@tailwindcss/vite`) | Pedido explícito |
| Render | SSR (adaptador `@astrojs/node`, modo `standalone`) | Necesario para Actions, sesiones y datos que cambian en cada petición |
| Mutaciones | Astro Actions (`astro:actions`) | Evita escribir una API REST a mano; validación con Zod incluida |
| Sesión de usuario | Astro Sessions (filesystem, vía adaptador Node) | Selector simple de "quién soy", no un login real — fuera del alcance pedido |
| "Base de datos" | Ficheros JSON en `data/`, leídos/escritos con `node:fs` | Sin pedir una BD, y añadir un motor real habría sido sobre-ingeniería para el alcance |
| Fuentes | "Press Start 2P" (títulos) + "Pixelify Sans" (cuerpo) | Pixel art reconocible pero legible en párrafos largos |

Ninguna dependencia fuera de `astro`, `@astrojs/node` y `tailwindcss`/`@tailwindcss/vite` — cero librerías de UI, cero ORM, cero gestor de estado.

## 3. Arquitectura

```
src/
├── actions/index.ts        # Mutaciones: addGame, setStatus, createList, deleteList,
│                            #   addGameToList, removeGameFromList
├── lib/db.ts                # Toda la lectura/escritura de data/*.json. Único punto
│                            #   de acceso a datos — nada más en el proyecto toca fs directamente
├── layouts/Layout.astro     # Header + nav + botón volver + <ClientRouter/> + sesión actual
├── components/
│   ├── PixelPanel.astro     # Contenedor con borde/sombra pixel
│   ├── PixelButton.astro    # Botón o enlace según reciba `href`; reenvía el resto de props
│   ├── StatusBadge.astro    # Pastilla de color por estado
│   ├── StatusSelector.astro # Formulario de cambio de estado (6 botones)
│   ├── ListCard.astro       # Tarjeta de lista (nombre + nº de juegos)
│   └── CoverPlaceholder.astro # Carátula placeholder (checker "textura ausente" + inicial)
└── pages/
    ├── index.astro          # Landing: hero a pantalla completa + 2 secciones
    ├── entrar.astro         # Selector de usuario (no es login real)
    ├── juegos/
    │   ├── index.astro      # Catálogo + búsqueda + toggle cuadrícula/lista
    │   ├── [id].astro       # Detalle + cambio de estado + añadir a lista
    │   └── nuevo.astro      # Alta de juego
    ├── listas/
    │   ├── index.astro      # Mis listas + crear
    │   └── [id].astro       # Gestionar juegos de una lista
    └── usuarios/
        ├── index.astro      # Directorio de usuarios
        └── [username].astro # Perfil público: biblioteca por estado + sus listas
```

Decisión deliberada: la tarjeta de juego (en el catálogo) **no** es un componente — se mantiene inline en cada página donde aparece, a petición explícita, para no abstraer algo que todavía no repite lógica compleja.

## 4. Modelo de datos

Cuatro colecciones en `data/*.json`, cada una un array plano, sin relaciones por FK real (solo IDs referenciados por convención):

- **`games.json`** — `{ id, title, platform, year?, coverUrl?, description? }`
- **`users.json`** — `{ username, displayName }` (semilla fija, no hay registro)
- **`library.json`** — `{ username, gameId, status, updatedAt }` (estado de un juego para un usuario)
- **`lists.json`** — `{ id, username, name, description?, gameIds: [] }`

Todo el acceso pasa por `src/lib/db.ts`, que expone funciones (`getGames`, `addGame`, `setStatus`, `createList`...) en vez de dejar que las páginas lean/escriban JSON a mano. Esto es lo único que habría que sustituir para migrar a una base de datos real.

## 5. Funcionalidades implementadas

- Ver detalle de un juego, buscar en el catálogo, añadir uno si no existe (con detección de duplicados por título).
- Cambiar el estado de un juego por usuario (6 estados).
- Crear, ver y gestionar listas (añadir/quitar juegos, borrar lista).
- Ver otros usuarios, su biblioteca agrupada por estado, y sus listas.
- Vista cuadrícula/lista en el catálogo, con la preferencia guardada en `localStorage`.
- Selector de usuario (sin contraseña) persistido en sesión de servidor.
- Botón de volver atrás (navegación real del navegador, oculto en la home).

## 6. Decisiones de UI/UX

- **Tema**: fondo oscuro (no negro puro), acentos saturados (verde/magenta/cian) solo en elementos interactivos/estado, nunca en bloques largos de texto — para mantener legibilidad.
- **Hero de la landing**: a ancho y alto completos de pantalla (técnica *full-bleed* + `100dvh` menos la altura fija de la cabecera), con tres capas de fondo animadas (rejilla pixel + aurora de color + barrido de luz tipo CRT) y texto con sombra fija para garantizar contraste contra cualquier zona del fondo en movimiento.
- **Animaciones**: deliberadamente sutiles en el resto de la app (lift al hover en tarjetas, crossfade entre páginas vía `<ClientRouter />`), más vistosas solo en el hero porque es la primera impresión. Todas respetan `prefers-reduced-motion` mediante una única regla CSS global.
- **Responsive**: mobile-first donde importa (cabecera, catálogo, formularios); verificado explícitamente en 375px/768px/1280px, no solo "debería funcionar con Tailwind".

## 7. Bugs reales encontrados durante el desarrollo (y por qué importan)

Todos se descubrieron ejecutando la app en un navegador real (Chrome vía Playwright), no leyendo el código:

1. **`prerender = true` en la home rompía la sesión.** El `Layout` necesita `Astro.session` en cada página; una página prerenderizada no tiene petición HTTP real en build time. Lección: con sesiones de servidor, cuidado con marcar páginas como estáticas.
2. **`PixelButton` no reenviaba `id` ni otros atributos.** Astro no pasa automáticamente props no declaradas en la interfaz `Props` al elemento renderizado. Se arregló capturando `...rest` y haciendo spread. Rompía silenciosamente el toggle de vista cuadrícula/lista (el botón no tenía `id`, el script no lo encontraba).
3. **Paneles con `max-w-*` sin `mx-auto` no quedaban centrados.** `max-w-*` solo limita el ancho; sin margen automático, el bloque se queda pegado a la izquierda del contenedor padre si este es más ancho.
4. **El buscador y el toggle de vista se solapaban en móvil (375px).** El contenedor tenía `flex-wrap`, pero un `min-w-48` dentro del formulario competía por espacio en vez de cederlo — resultado: overflow silencioso (visualmente, el botón "BUSCAR" se renderizaba cortado/solapado). Solo se detectó midiendo anchos reales de cada elemento, no a simple vista en el código.
5. **El primer barrido de luz del hero era casi invisible.** Usaba tonos verde/cian iguales a los de la aurora de fondo, así que se camuflaba. Se cambió a blanco con pico de opacidad para garantizar contraste independientemente del color de fondo en ese instante.

## 8. Cómo se verificó (no solo "debería funcionar")

En cada iteración: servidor de desarrollo real (`npm run dev`) + un Chrome real controlado por Playwright (headless) que navega, hace clic, rellena formularios y hace capturas de pantalla — no simulación, ejecución real. Se comprobó, en varios momentos del desarrollo:

- Ausencia de errores en consola del navegador tras cada cambio.
- Medición de anchos/alturas reales de elementos (`getBoundingClientRect`) para detectar overflow que no se ve "a simple vista" en un screenshot.
- Capturas en tres anchos de pantalla (375 / 768 / 1280px) y en varios momentos de las animaciones (para comprobar que efectivamente se mueven, no solo que el CSS "debería" animarlas).
- El flujo completo de usuario: entrar como un usuario, cambiar estado de un juego, crear una lista, añadir un juego a ella, y verlo reflejado en el perfil público de otro usuario.

## 9. Limitaciones conocidas / deuda técnica asumida

- **Persistencia en JSON de disco**: no soporta escrituras concurrentes de verdad (sin locks), y no funciona en plataformas serverless/edge (necesita disco persistente). Aceptable para el alcance pedido; el punto de entrada para cambiarlo es únicamente `src/lib/db.ts`.
- **No hay autenticación real**: el selector de usuario no tiene contraseña ni protección — cualquiera puede "ser" cualquier usuario de la lista. Decisión explícita de alcance, no un descuido.
- **`GameCard` no es un componente**: a petición explícita, para no abstraer antes de tener una razón real de peso.
- **El cambio de vista cuadrícula/lista no está animado**: animar un reflow de `grid` a `flex` necesitaría una técnica FLIP; se dejó fuera por complejidad frente al beneficio.
- **Sin tests automatizados**: la verificación se ha hecho con pruebas manuales dirigidas por un navegador real en cada iteración, no con una suite de tests que quede en el repo.

## 10. Posibles próximos pasos

- Sustituir `src/lib/db.ts` por SQLite (`better-sqlite3`) o una BD remota si se necesita concurrencia real o despliegue serverless.
- Login real con contraseña.
- Carátulas de verdad vía una API externa de videojuegos (ya hay el campo `coverUrl` y el fallback a placeholder preparado).
- Paginación en `/juegos` cuando el catálogo crezca.
- Primer commit — el repositorio tiene `git init` pero **cero commits**; todo el trabajo descrito aquí sigue sin confirmar en el historial.

## 11. Cómo ejecutar

```bash
cd game-library
npm install    # si no se ha hecho ya
npm run dev    # http://localhost:4321
```

Para producción: `npm run build && npm run start` (servidor Node standalone — no es un sitio estático, necesita un proceso persistente y disco para `data/`).
