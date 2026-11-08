# Spec: API GraphQL de consulta

| | |
|---|---|
| **Status** | In review |
| **Author** | |
| **Date** | 2026-11-08 |

## 1. Context

La Entrega 3 (`roboleague-entrega-3.pdf`) pide como propuesta de valor exponer el dominio de consulta
mediante una API GraphQL que conviva con la API REST existente. Los consumidores de una plataforma de
competencias son variados (pantallas de resultados en vivo, aplicaciones de los equipos, prensa) y
cada uno necesita una combinación distinta de los mismos datos. Con REST, una pantalla que muestra la
tabla con el nombre y el robot de cada equipo, o una app que muestra los resultados de un equipo por
ronda con el desglose de cada puntaje, hace varias peticiones y recibe datos que no usa. GraphQL
permite pedir exactamente esos campos en una sola consulta anidada.

La API es un adaptador de entrada más: los resolvers delegan en los puertos de entrada del núcleo
(`FindCompetition`, `FindTeamRegistration`, `FindRound`, `FindRunResult`, `CalculateRunScore`,
`GetStandings`) y en los puertos de consulta que esta spec necesita y todavía no existen
(ver [`PLAN_graphql.md`](../plans/PLAN_graphql.md), 4.3). El puntaje, la selección de rondas, los
desempates y la explicación los sigue calculando el núcleo, igual que para REST
(`SPEC_consultar-puntaje-explicable.md`, `SPEC_gestionar-posiciones.md`).

## 2. Functional requirements

**FR-1 — Punto de entrada.** La API GraphQL se atiende en `POST /graphql`, con el contrato estándar de
GraphQL sobre HTTP (`query`, `variables`, `operationName`; respuesta con `data` y `errors`). La API REST
sigue en sus rutas actuales.

**FR-2 — Consultas raíz.** Se puede empezar una consulta por:
`competition(id)`, `team(id)`, `round(id)`, `run(id)` y `standings(competitionId, categoryId)`.

**FR-3 — Competencia.** Una competencia informa `id`, `name`, `period` (inicio y fin),
`activeRulebookVersion` y sus `categories`.

**FR-4 — Categoría.** Una categoría informa `id`, `name`, `ageRange`, `robotClass`, sus `teams`, sus
`rounds` ordenadas por ordinal y su tabla de posiciones vigente (`standings`).

**FR-5 — Equipo.** Un equipo informa `id`, `name`, `status` de la inscripción, su `robot` (`name` y
`robotClass`), su `category` y sus `results`.

**FR-6 — Resultados de un equipo por ronda.** `Team.results` agrupa las corridas del equipo por ronda:
cada elemento tiene la `round` y las `runs` del equipo en ella, ordenadas por ordinal de ronda y número
de intento.

**FR-7 — Ronda.** Una ronda informa `id`, `ordinal`, `challengeId`, `rulebookVersion`, sus `heats`
(equipo, pista, horario de inicio y fin, jueces) y sus `runs`.

**FR-8 — Corrida.** Una corrida informa `id`, `attemptNumber`, `status` (`CAPTURED` o `CORRECTED`), su
`team`, su `round` y su `score`.

**FR-9 — Desglose del puntaje (`ScoreBreakdown`).** El puntaje de una corrida informa
`completionStatus`, `missingSources`, `rulebookVersion`, `total` y `contributions` (código de regla,
naturaleza, explicación y puntos), y cuando corresponde `bonusCap` (obtenido, tope, recorte y aplicado)
y `bySource` (subtotal y contribuciones de cada fuente, más las que cruzan fuentes). Es la misma
información que `GET /runs/{runId}/score`.

**FR-10 — Corrida pendiente.** Una corrida mixta que espera una fuente informa `completionStatus`
`PENDING` y sus `missingSources`; `total`, `contributions`, `bonusCap` y `bySource` son `null`. No hay
puntaje parcial.

**FR-11 — Posiciones.** Una tabla informa `revision`, `status` (`PROVISIONAL` o `FINAL`),
`rulebookVersion`, `generatedAt` y sus `entries`: `position`, `team`, `totalPoints`, `appliedTiebreaks`
(código y descripción) y `explanation` (subtotales con código, descripción, puntos y el detalle de
rondas contadas y descartadas). Es la última revisión, igual que `GET …/standings`.

**FR-12 — Consulta anidada del enunciado.** Equipo, resultados por ronda y desglose del puntaje de cada
corrida se resuelven en una sola petición.

**FR-13 — Errores.** Un error de una consulta se informa en `errors` con `message`, `path` y
`extensions.classification`, y con `extensions.code` igual al código de la API REST para la misma
excepción (`NOT_FOUND`, `INVALID_VALUE`, `RULE_VIOLATION`, `CONFLICT`, `DOMAIN_ERROR`). Un campo que no
existe en el esquema se rechaza antes de ejecutar la consulta.

**FR-14 — Consola de demostración.** GraphiQL está disponible para escribir y ejecutar consultas
durante la presentación.

## 3. Constraints (non-functional)

**C-1 — Solo delegación.** Los resolvers sólo invocan puertos de entrada y traducen. No calculan
puntajes, no ordenan posiciones, no eligen rondas ni filtran por reglas de negocio. Separar una
corrida pendiente de una completa usa el estado de completitud que informa el núcleo, como hace REST.

**C-2 — Contrato propio.** El esquema y sus tipos son del adaptador GraphQL. No se reutilizan DTOs ni
mappers de `adapter.in.web` y no se exponen agregados del dominio tal cual.

**C-3 — Solo lectura.** No hay mutaciones: crear, capturar, corregir, publicar y apelar siguen siendo
operaciones REST.

**C-4 — Datos que no se exponen.** Integrantes (nombre completo y fecha de nacimiento: son menores),
documentación, motivos de rechazo de una inscripción, auditoría, temporadas y apelaciones.

**C-5 — N+1 tratado.** Cargar el equipo de varias filas de una tabla o de varias corridas no hace una
llamada por fila: los pedidos de un mismo nivel se agrupan en una sola llamada de lote, y un equipo
que reaparece en otro nivel de la misma consulta no se vuelve a cargar (caché por petición, que dura
lo que dura la petición). La evidencia es una prueba que cuenta las invocaciones de los puertos.

**C-6 — Convivencia con REST.** Agregar GraphQL no cambia el contrato ni el comportamiento de la API
REST: todas sus pruebas siguen pasando sin modificarse. Ambas usan los mismos puertos y la misma
persistencia (`memory` o `jpa`).

**C-7 — Puntos exactos.** Los puntos se exponen con un escalar propio `Points`, serializado como
número JSON decimal exacto, sin pasar por `Float`.

**C-8 — Identificadores.** Los ids son `ID` con el mismo texto que usa REST (`TEAM-1`, `RUN-3`…).

**C-9 — Límites verificados.** ArchUnit verifica que el adaptador GraphQL no dependa de
`adapter.in.web`, `infrastructure`, `config`, interactors (`*UseCase`) ni repositorios, y que REST no
dependa de GraphQL.

## 4. Acceptance criteria

**AC-1 — Competencia con categorías.**
Given una competencia con dos categorías,
When se consulta `competition(id)` pidiendo `name` y `categories { name }`,
Then la respuesta trae sólo esos campos y las dos categorías.

**AC-2 — Equipos y rondas de una categoría.**
Given una categoría con dos equipos inscriptos y dos rondas programadas,
When se consulta la categoría con `teams { name status }` y `rounds { ordinal }`,
Then aparecen ambos equipos con su estado y las rondas ordenadas por ordinal.

**AC-3 — Equipo, resultados por ronda y desglose en una consulta.**
Given un equipo con corridas capturadas en dos rondas,
When se consulta `team(id) { results { round { ordinal } runs { attemptNumber score { total contributions { ruleCode points } } } } }`
en una sola petición,
Then la respuesta trae las dos rondas en orden, cada corrida con su desglose,
and el `total` y las contribuciones de cada corrida coinciden con `GET /runs/{runId}/score`.

**AC-4 — Posiciones con nombre del equipo.**
Given una tabla generada para una categoría,
When se consulta `standings(competitionId, categoryId) { entries { position totalPoints team { name robot { name } } } }`,
Then cada fila trae la posición, los puntos y el nombre y robot del equipo,
and posiciones y puntos coinciden con `GET …/standings`.

**AC-5 — Tope de bonificaciones y fuentes.**
Given una corrida que supera el tope de bonificaciones y una corrida mixta completa,
When se consulta el `score` de cada una,
Then la primera trae `bonusCap` con obtenido, tope, recorte y aplicado,
and la segunda trae `bySource` con el subtotal de cada fuente, que recompone el total.

**AC-6 — Corrida pendiente.**
Given una corrida mixta que sólo recibió la fuente automática,
When se consulta su `score`,
Then `completionStatus` es `PENDING`, `missingSources` es `["JUDGES"]` y `total` es `null`.

**AC-7 — Reglamento histórico.**
Given una corrida capturada con una versión del reglamento y una versión nueva publicada después,
When se consulta su `score`,
Then `rulebookVersion` es la de la corrida y el total es el calculado con ella.

**AC-8 — Entidad no encontrada.**
Given un id que no existe,
When se consulta `team(id: "TEAM-404") { name }`,
Then `data.team` es `null`,
and `errors` trae `extensions.code` `NOT_FOUND` y el `path` `["team"]`.

**AC-9 — Campo inexistente.**
Given una consulta que pide un campo que no está en el esquema (por ejemplo `team { birthDate }`),
When se envía,
Then la respuesta no ejecuta ningún resolver y trae un error de validación que nombra el campo.

**AC-10 — Valor inválido.**
Given un id con formato inválido (por ejemplo vacío),
When se consulta,
Then `errors` trae `extensions.code` `INVALID_VALUE`.

**AC-11 — Error parcial.**
Given una competencia con una categoría que todavía no tiene tabla generada,
When se consulta `competition(id) { name categories { name standings { revision } } }`,
Then `name` y las categorías llegan,
and `standings` de esa categoría es `null` con un error `NOT_FOUND` cuyo `path` la identifica.

**AC-12 — N+1: un lote por nivel.**
Given una tabla con N equipos,
When se consulta `standings { entries { team { name } } }`,
Then el puerto de lote de equipos se invoca una vez y el de un equipo ninguna.

**AC-13 — N+1: caché por petición.**
Given una consulta en la que el mismo equipo aparece en dos niveles
(`standings { entries { team { name results { runs { team { name } } } } } }`),
When se resuelve,
Then ese equipo se pide a los puertos una sola vez en toda la petición,
and una segunda petición vuelve a pedirlo (la caché no se comparte entre peticiones).

**AC-14 — REST intacta.**
Given la API REST existente,
When se ejecutan sus pruebas con GraphQL incorporado,
Then todas pasan sin modificaciones.

## 5. Out of scope

- Mutaciones y *subscriptions* (incluida la actualización del ranking en vivo, opcional en la
  consigna).
- Paginación, filtros y ordenamientos elegidos por el cliente.
- Cliente GraphQL en el frontend.
- Autenticación, autorización y límites de profundidad o complejidad de las consultas.
- Historial de revisiones de posiciones, corridas pendientes de la tabla, auditoría, apelaciones,
  temporadas e integrantes.
- Resolver con lotes los N+1 de `Run.score` (carga el reglamento de cada corrida) y de
  `Team.results → round`: quedan documentados como deuda técnica.

## 6. Open questions

Para resolver antes de implementar:

- **OQ-1 — Equipos de una categoría.** ¿`Category.teams` devuelve todas las inscripciones con su
  `status` o sólo las aceptadas? Propuesta: todas con `status`. Filtrar por "participa" sería una regla
  en el adaptador, y una pantalla puede mostrar u ocultar según el estado.
- **OQ-2 — GraphiQL.** ¿Se habilita sólo con el perfil `dev` o siempre? Propuesta: sólo con `dev`, que
  es el que se usa en la demo con datos de demostración; en otros perfiles queda sólo `/graphql`.
- **OQ-3 — Categoría sin tabla.** ¿`Category.standings` sin tabla generada responde `null` con un error
  `NOT_FOUND` (AC-11) o `null` sin error? Propuesta: con error. Es la misma excepción de `GetStandings`
  y el adaptador no decide en silencio qué ausencias son normales.
