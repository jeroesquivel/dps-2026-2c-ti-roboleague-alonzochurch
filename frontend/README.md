# Frontend

Interfaz web de RoboLeague con React, TypeScript y Vite. Consume la API REST del backend; no decide
reglas de negocio (la elegibilidad la decide siempre el backend).

## Requisitos

- Node.js 20 o superior.
- Backend en ejecución en `http://localhost:8080` (ver el README de la raíz).

## Comandos

```bash
npm install      # instalar dependencias
npm run dev      # servidor de desarrollo en http://localhost:5173
npm test         # pruebas (Vitest + Testing Library)
npm run build    # comprobación de tipos y build de producción en dist/
```

## Comunicación con el backend

En desarrollo el navegador sólo habla con el origen de Vite (`localhost:5173`). `vite.config.ts`
define un proxy que reenvía `/teams`, `/rounds` y `/competitions` a `http://localhost:8080`, por lo que **no hace falta
configurar CORS** en Spring. Para apuntar a otro backend:

```bash
BACKEND_URL=http://localhost:9090 npm run dev
```

Si el frontend se sirviera desde otro origen que el de la API, haría falta CORS en el backend o un
proxy equivalente. Al agregar un endpoint nuevo hay que sumar su prefijo al proxy.

## Estructura

```text
src/api/http.ts            cliente HTTP compartido: JSON, ApiRequestError {code, message, conflicts}
src/App.tsx                navegación entre Equipos y Rondas
src/features/teams/
  api.ts                   POST /teams
  types.ts                 contrato de la API según el README de la raíz
  formModel.ts             estado del formulario, validación de presencia/formato y armado del request
  RegisterTeamForm.tsx     formulario: integrantes, robot y documentación
  RegistrationResult.tsx   aceptación o rechazo con cada violación
src/features/rounds/
  api.ts                   POST /rounds y GET /rounds/{id}
  types.ts                 contrato de la API de rondas
  formModel.ts             estado del formulario, validación y armado del request (jueces separados por coma)
  ScheduleRoundForm.tsx    formulario: datos de la ronda y largadas agregables/quitables
  RoundView.tsx            ronda programada con versión del reglamento y tabla de largadas
  ScheduleConflicts.tsx    conflictos de agenda (equipo, pista o juez ocupado)
src/features/standings/
  api.ts                   generar, consultar la última revisión, historial y publicar
  types.ts                 contrato de la API de posiciones
  labels.ts                etiquetas en español y formato de puntos e instantes
  StandingsPage.tsx        formulario de categoría y acciones (consultar, generar, historial, publicar)
  StandingsView.tsx        tabla de una revisión, corridas pendientes y explicación por equipo
  RevisionList.tsx         selector de revisiones del historial
```

## Flujos implementados

- **Registrar equipo** — spec [`SPEC_registrar-equipo`](../.docs/specs/SPEC_registrar-equipo.md)
  (FR-7, AC-6). El ID de la competencia y de la categoría se ingresan a mano: todavía no hay un
  endpoint para listarlos (queda para el spec de configurar evento).
- **Programar ronda** — spec [`SPEC_programar-rondas`](../.docs/specs/SPEC_programar-rondas.md)
  (FR-8). Se agregan o quitan largadas, y al enviar se muestra la ronda (consultada con
  `GET /rounds/{id}`) o, ante un `409 SCHEDULE_CONFLICT`, cada conflicto con su tipo. El campo de
  inicio es un `datetime-local`: se envía como hora local de la competencia, sin zona. Los IDs de
  competencia, categoría, desafío, equipo y pista se ingresan a mano.
- **Posiciones** — spec [`SPEC_gestionar-posiciones`](../.docs/specs/SPEC_gestionar-posiciones.md)
  (FR-10). Se consulta o se genera la tabla de una categoría, se distinguen las revisiones
  provisionales y finales, se ven las corridas pendientes y la explicación de cada puntaje (rondas
  computadas y descartadas), y se publica como final. El botón de publicar aparece sólo en la última
  revisión provisional; si el servidor la rechaza (corridas o apelaciones pendientes) se muestra su
  mensaje. La pantalla no ordena ni decide desempates: muestra lo que devuelve la API. Recalcular
  queda sólo en la API. Para probarlo con datos, arrancar el backend con el perfil `dev` (ver el
  README de la raíz) y usar `COMPETITION-1` y `CATEGORY-1`.
