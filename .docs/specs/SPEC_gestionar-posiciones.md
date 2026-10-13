# Spec: Generar, consultar y publicar posiciones

| | |
|---|---|
| **Status** | Draft |
| **Author** | |
| **Date** | 2026-10-12 |

## 1. Context

El organizador necesita generar una tabla provisional por competencia y categoría, consultar sus
revisiones y publicarla como definitiva cuando no existen resultados ni apelaciones pendientes. Los
equipos necesitan comprender su posición, puntaje, desempates y selección de rondas.

El núcleo ofrece `GenerateStandings`, `GetStandings`, `PublishStandings` y
`RecalculateStandings`. `StandingsHistory` protege el ciclo de vida y `CategoryScoringService`
calcula entradas, pendientes y explicaciones. La selección F1 está definida en
`SPEC-mejores-n-de-m-rondas.md`.

## 2. Functional requirements

**FR-1 — Generar provisional.** El usuario puede generar la primera tabla provisional de una
categoría usando el reglamento activo.

**FR-2 — Entradas ordenadas.** La tabla muestra posición, equipo, total, desempates aplicados y
explicación del total para cada participante con resultados computables.

**FR-3 — Resultados pendientes.** La tabla provisional identifica las corridas incompletas que no
fueron puntuadas.

**FR-4 — Mejores rondas.** Para desafíos con mejores N de M, cada entrada expone rondas consideradas,
descartadas, motivos y subtotales según la spec F1.

**FR-5 — Consultar historial.** El usuario puede consultar la última revisión y todas las revisiones
anteriores de la categoría, preservando estado y explicación de cada una.

**FR-6 — Publicar definitiva.** Una revisión provisional puede publicarse como final sólo si no hay
corridas ni apelaciones pendientes.

**FR-7 — Cierre ordinario.** Después de publicar standings finales, las capturas ordinarias no pueden
modificar la categoría.

**FR-8 — Recalcular.** El organizador puede recalcular standings existentes indicando un motivo. El
resultado es una revisión nueva y provisional; las anteriores no cambian.

**FR-9 — Recálculo por apelación.** Una apelación aceptada sobre una categoría con standings provoca
automáticamente una nueva revisión con la corrección aplicada.

**FR-10 — Interfaz mínima.** El frontend permite generar, consultar revisiones, distinguir
provisional/final, observar pendientes y publicar cuando el dominio lo permite.

## 3. Constraints (non-functional)

**C-1 — Ranking en el dominio.** Controllers y frontend no ordenan equipos, aplican desempates ni
seleccionan rondas.

**C-2 — DTO de posiciones.** `Standings`, `StandingEntry` y `ScoreExplanation` se proyectan a DTOs
que preservan la explicación sin convertir los agregados en contrato público.

**C-3 — Revisiones inmutables.** La representación web conserva revisión, fecha, versión del
reglamento y estado para que el historial sea inequívoco.

**C-4 — Repositorio real en memoria.** Las pruebas de API usan `InMemoryStandingsRepository` y deben
respetar su contract test de reemplazo de una revisión provisional y conservación del historial.

**C-5 — F1 intacta.** Esta spec sólo expone la explicación ya definida por
`SPEC-mejores-n-de-m-rondas.md`.

## 4. Acceptance criteria

**AC-1 — Primera tabla provisional.**
Given una categoría con resultados completos,
When se generan posiciones,
Then la API devuelve revisión 1 provisional con las entradas en el orden calculado por el dominio.

**AC-2 — Pendientes visibles.**
Given una categoría con una corrida mixta pendiente,
When se generan posiciones,
Then la corrida no aporta puntaje y figura entre las pendientes.

**AC-3 — Publicación bloqueada por corrida.**
Given standings provisionales con una corrida pendiente,
When se intenta publicar,
Then la API responde con conflicto y mantiene la revisión provisional.

**AC-4 — Publicación bloqueada por apelación.**
Given standings provisionales y una apelación pendiente de la categoría,
When se intenta publicar,
Then la API responde con conflicto e identifica la apelación pendiente.

**AC-5 — Publicación exitosa.**
Given standings provisionales sin corridas ni apelaciones pendientes,
When se publican,
Then la misma revisión queda final y puede consultarse como última.

**AC-6 — Recálculo explícito.**
Given standings existentes,
When el organizador solicita recálculo con un motivo,
Then se crea la revisión siguiente como provisional;
and el historial conserva la revisión anterior sin cambios.

**AC-7 — Mejores rondas explicadas.**
Given un desafío configurado con mejores N de M,
When se consultan posiciones,
Then cada entrada muestra las rondas `COUNTED` y `DISCARDED`, sus motivos y subtotales.

**AC-8 — Consulta inexistente.**
Given una categoría sin standings,
When se consulta o publica,
Then la API responde como recurso no encontrado.

## 5. Out of scope

- Edición manual del orden o del puntaje desde la tabla.
- Eliminación de revisiones.
- Actualización en tiempo real o notificaciones.
- Ranking global entre categorías o competencias.
- Exportación de standings.

## 6. Open questions

Resueltas:

- **Consulta e historial.** Se separan: `GET …/standings` devuelve sólo la última revisión y
  `GET …/standings/revisions` devuelve todas, de la primera a la última, cada una completa con su
  explicación, estado, fecha y versión de reglamento. El frontend carga el historial sólo cuando se lo
  pide.
- **Identificación del equipo.** El adaptador web consulta `FindTeamRegistration` y agrega `teamName`
  a cada entrada y a cada corrida pendiente, una vez por equipo y por petición. No se modifica
  `StandingEntry`: el nombre es el actual de la inscripción, no el de la fecha de la revisión.
- **Motivo del recálculo.** `RecalculateStandings.Command` rechaza un motivo ausente o en blanco
  (`InvalidValueException`, `400`); antes un `null` fallaba al auditar.
- **Apelación pendiente.** La publicación bloqueada responde `409 CONFLICT` y el `message` incluye los
  identificadores de las apelaciones pendientes; el texto no es parte del contrato.
