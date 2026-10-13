# RoboLeague

RoboLeague modela una plataforma de competencias de robótica con arquitectura hexagonal. El
repositorio separa el núcleo Java puro de una aplicación web Spring Boot preparada para incorporar
los flujos funcionales de `.docs/specs/` en entregas posteriores.

## Requisitos

- JDK 21 o superior
- Maven 3.9 o superior

## Estructura

```text
core/       dominio, puertos e interactors; no depende de Spring ni de HTTP
backend/    Spring Boot, composición, adaptadores en memoria, demo y entrada HTTP técnica
frontend/   directorio reservado; la tecnología aún no fue elegida
```

El `pom.xml` raíz es un agregador Maven. `backend` depende de `core`; el núcleo no conoce al
backend. Los repositorios actuales son adaptadores en memoria y su contenido se pierde al reiniciar.

## Compilar y verificar

Desde la raíz:

```bash
mvn clean compile
mvn test
mvn verify
```

`mvn verify` verifica ambos módulos. El informe de cobertura del núcleo queda en
`core/target/site/jacoco/index.html` y exige 100 % de instrucciones, ramas, líneas, complejidad,
métodos y clases.

Para ejecutar una prueba concreta:

```bash
mvn -pl core -Dtest=ScoringRulesTest test
mvn -pl backend -am -Dtest=RoboLeagueApplicationTest test
```

## Ejecutar el backend

```bash
mvn -pl backend -am package
java -jar backend/target/roboleague-backend-1.0-SNAPSHOT.jar
```

La comprobación técnica no consulta repositorios:

```text
GET http://localhost:8080/technical/health
200 {"status":"UP"}
```

### Datos de demostración (perfil `dev`)

Con los repositorios en memoria, el backend arranca vacío y ninguna API crea competencias todavía. Para
usar el frontend de punta a punta se puede arrancar con el perfil `dev`, que carga una competencia con
una categoría, cuatro equipos, seis rondas y sus resultados:

```bash
java -jar backend/target/roboleague-backend-1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

Los datos se declaran en [`application-dev.yaml`](backend/src/main/resources/application-dev.yaml) y se
cargan por los puertos de entrada del núcleo. Los identificadores son secuenciales y siempre los mismos:
competencia `COMPETITION-1`, categoría `CATEGORY-1`, equipos `TEAM-1` a `TEAM-4` (Kappa Labs, Delta
Bots, Omega Crew y Sigma Works), rondas `ROUND-1` a `ROUND-6`. No se generan posiciones: eso se hace
desde la API o la pantalla. La corrida de SHOWCASE de Omega Crew queda pendiente (falta el jurado), así
que publicar da conflicto hasta que se complete. Sin el perfil `dev` no se carga nada.

## API: equipos

Spec: [`.docs/specs/SPEC_registrar-equipo.md`](.docs/specs/SPEC_registrar-equipo.md). El controller
sólo invoca los puertos `RegisterTeam` y `FindTeamRegistration`; la elegibilidad la decide el
reglamento activo de la competencia.

### `POST /teams` — registrar un equipo

```json
{
  "competitionId": "competition-1",
  "categoryId": "category-1",
  "teamName": "Rescue Crew",
  "members": [
    {"fullName": "Ada", "birthDate": "2010-05-20", "role": "COMPETITOR"},
    {"fullName": "Grace", "birthDate": "1988-02-10", "role": "COACH"}
  ],
  "robot": {
    "name": "Rescuer", "robotClass": "RESCUE_BOT", "weightKg": 2.4,
    "dimensions": {"lengthMm": 180, "widthMm": 180, "heightMm": 150}
  },
  "documents": [{"type": "PARENTAL_CONSENT", "reference": "PC-1"}],
  "actor": "organizer"
}
```

- `role`: `COMPETITOR` o `COACH`. `type`: `PARENTAL_CONSENT`, `INSURANCE`, `TECHNICAL_SHEET` o
  `SAFETY_CHECK`. `birthDate` usa formato ISO (`yyyy-MM-dd`).
- `documents` es opcional: si falta se toma como lista vacía y la inscripción queda rechazada por los
  documentos requeridos que el reglamento exija.
- `actor` es obligatorio y se registra en la auditoría; esta etapa no tiene autenticación.

Respuesta `201 Created` con `Location: /teams/{teamId}`, tanto si el equipo queda aceptado como si
queda rechazado (en ambos casos la inscripción existe y puede consultarse):

```json
{
  "teamId": "team-1",
  "status": "REJECTED",
  "violations": [
    {"code": "REQUIRED_DOCUMENTS", "reason": "missing document TECHNICAL_SHEET"}
  ]
}
```

`status` es `ACCEPTED` o `REJECTED`. `violations` enumera **todas** las violaciones detectadas. Cada
una lleva el `code` estable de su regla (`AGE_RANGE`, `TEAM_COMPOSITION`, `ROBOT_CLASS`,
`ROBOT_SPECIFICATION`, `REQUIRED_DOCUMENTS`) y una descripción en `reason`; el texto de `reason` no es
estable y no debe interpretarse.

### `GET /teams/{teamId}` — consultar una inscripción

`200` con `teamId`, `competitionId`, `categoryId`, `name`, `status`, `members` (con `memberId`,
`fullName`, `birthDate`, `role`), `robot`, `documentTypes` (sólo los tipos presentados, no sus
referencias) y `violations` con el mismo formato que arriba.

### Errores

Todos los errores tienen la forma `{"code": "...", "message": "..."}`. El manejador compartido
(`ApiExceptionHandler`) traduce la jerarquía del dominio:

| Excepción del dominio | HTTP | `code` |
| --- | --- | --- |
| `InvalidValueException` (incluye campos ausentes o con formato inválido) | 400 | `INVALID_VALUE` |
| Cuerpo que no es JSON válido | 400 | `MALFORMED_REQUEST` |
| `NotFoundException` (competencia, categoría ajena o inscripción inexistente) | 404 | `NOT_FOUND` |
| `ScheduleConflictException` (agenda de equipo, pista o juez) | 409 | `SCHEDULE_CONFLICT` (con `conflicts`) |
| `ConflictException` (ordinal repetido, límite de rondas) | 409 | `CONFLICT` |
| `RuleViolationException` | 422 | `RULE_VIOLATION` |
| Otra `DomainException` | 422 | `DOMAIN_ERROR` |

## API: rondas

Spec: [`.docs/specs/SPEC_programar-rondas.md`](.docs/specs/SPEC_programar-rondas.md). El controller
sólo invoca los puertos `ScheduleRound` y `FindRound`; ordinales, límites de rondas, elegibilidad de
los equipos y conflictos de agenda los deciden `CompetitionSchedule`, `Round` y
`ScheduleConflictDetector`.

### `POST /rounds` — programar una ronda

```json
{
  "competitionId": "competition-1",
  "categoryId": "category-1",
  "challengeId": "RESCUE",
  "ordinal": 1,
  "heats": [
    {
      "teamId": "team-1",
      "arenaId": "A1",
      "start": "2026-03-02T10:00:00",
      "durationMinutes": 5,
      "judges": ["judge-1", "judge-2"]
    }
  ],
  "actor": "organizer"
}
```

- `start` es una fecha y hora **local de la competencia**, en ISO sin zona ni offset
  (`yyyy-MM-ddTHH:mm:ss`, los segundos son opcionales). Nunca se interpreta en la zona del servidor y
  un valor con `Z` u offset se rechaza con `400`.
- `durationMinutes` es un entero positivo de minutos. Un heat termina en `start + durationMinutes`;
  dos heats consecutivos (uno termina cuando el otro empieza) no se superponen.
- `heats` debe tener al menos un heat y cada heat al menos un juez. `actor` es obligatorio.
- La operación es atómica: si algún heat falla no queda guardada ninguna parte de la ronda.

Respuesta `201 Created` con `Location: /rounds/{roundId}`:

```json
{"roundId": "round-1"}
```

### `GET /rounds/{roundId}` — consultar una ronda

```json
{
  "roundId": "round-1",
  "competitionId": "competition-1",
  "categoryId": "category-1",
  "challengeId": "RESCUE",
  "ordinal": 1,
  "rulebookVersion": "v1",
  "heats": [
    {
      "heatId": "heat-1",
      "teamId": "team-1",
      "arenaId": "A1",
      "start": "2026-03-02T10:00:00",
      "end": "2026-03-02T10:05:00",
      "durationMinutes": 5,
      "judges": ["judge-1", "judge-2"]
    }
  ]
}
```

`rulebookVersion` es la versión del reglamento activa cuando se programó la ronda. `start` y `end`
siempre incluyen segundos; `judges` se devuelve ordenado.

### Conflictos de agenda

Cuando uno o más heats se superponen con la agenda existente (de la misma ronda o de otras de la
competencia), la API responde `409` con el código `SCHEDULE_CONFLICT` y la lista tipada de conflictos:

```json
{
  "code": "SCHEDULE_CONFLICT",
  "message": "the heat cannot be scheduled: ARENA_BUSY (arena A1 is taken by heat heat-1)",
  "conflicts": [
    {"type": "ARENA_BUSY", "detail": "arena A1 is taken by heat heat-1"}
  ]
}
```

`conflicts` reúne todos los conflictos del primer heat que choca (puede haber varios tipos a la vez).
`type` es estable (`TEAM_BUSY`, `ARENA_BUSY` o `JUDGE_BUSY`); `detail` y `message` son descripciones que
no deben interpretarse. Los demás rechazos usan la tabla de errores de abajo: ordinal repetido o límite de
rondas «mejores N de M» alcanzado → `409 CONFLICT`; heat fuera del período de la competencia o equipo
no aceptado/de otra competencia → `422 RULE_VIOLATION`; equipo, competencia, categoría o desafío
inexistente → `404`.

## API: posiciones

Spec: [`.docs/specs/SPEC_gestionar-posiciones.md`](.docs/specs/SPEC_gestionar-posiciones.md). El controller
invoca los puertos `GenerateStandings`, `GetStandings`, `PublishStandings` y `RecalculateStandings`, y
`FindTeamRegistration` para agregar el nombre de cada equipo. El orden, los desempates y la selección
de rondas los decide el dominio; la API sólo los proyecta.

Todas las rutas cuelgan de `/competitions/{competitionId}/categories/{categoryId}/standings`.

| Operación | Ruta | Cuerpo | Respuesta |
| --- | --- | --- | --- |
| Generar la primera tabla provisional | `POST …/standings` | `{"actor": "organizer"}` | `201` con la revisión 1 y `Location: …/standings` |
| Consultar la última revisión | `GET …/standings` | — | `200` con la última revisión |
| Consultar todas las revisiones | `GET …/standings/revisions` | — | `200` `{"revisions": [...]}` de la primera a la última |
| Publicar como final | `POST …/standings/publication` | `{"actor": "organizer"}` | `200` con la misma revisión en estado `FINAL` |
| Recalcular | `POST …/standings/recalculation` | `{"reason": "…", "actor": "organizer"}` | `201` con la revisión siguiente, `PROVISIONAL` |

`actor` es obligatorio en las operaciones que modifican; `reason` debe ser no vacío (si no, `400`).
Una apelación aceptada sobre una categoría con posiciones también crea una revisión nueva, sin
llamar a este endpoint.

Cada revisión tiene esta forma:

```json
{
  "competitionId": "COMPETITION-1",
  "categoryId": "CATEGORY-1",
  "revision": 1,
  "status": "PROVISIONAL",
  "generatedAt": "2026-03-02T10:15:30.123Z",
  "rulebookVersion": "v1",
  "entries": [
    {
      "position": 1,
      "teamId": "TEAM-3",
      "teamName": "Omega Crew",
      "totalPoints": 71.50,
      "appliedTiebreaks": [{"code": "HIGHEST_SINGLE_RUN", "description": "highest single run"}],
      "explanation": {
        "subtotals": [
          {
            "code": "BEST_ROUNDS",
            "description": "best 2 of 3 rounds",
            "points": 71.50,
            "rounds": [
              {"ordinal": 3, "points": 40.00, "status": "COUNTED", "reason": "…"},
              {"ordinal": 4, "points": 31.50, "status": "COUNTED", "reason": "…"},
              {"ordinal": 5, "points": 20.00, "status": "DISCARDED", "reason": "…"}
            ]
          }
        ]
      }
    }
  ],
  "pendingRuns": [
    {"runId": "RUN-23", "teamId": "TEAM-4", "teamName": "Sigma Works", "roundId": "ROUND-6",
     "attemptNumber": 1, "missingSources": ["JUDGES"]}
  ]
}
```

- `status` es `PROVISIONAL` o `FINAL`; `generatedAt` es un instante UTC. Cada revisión conserva su
  propio estado, fecha y versión de reglamento: las anteriores nunca cambian.
- `entries` ya viene ordenado y con `position`; sólo incluye equipos con al menos una corrida
  completa. `totalPoints` y `points` son decimales con dos cifras.
- `rounds` de cada subtotal aparece sólo en desafíos con «mejores N de M»: `COUNTED` suma sus puntos y
  `DISCARDED` no; `reason` es una descripción que no debe interpretarse.
- `pendingRuns` lista las corridas incompletas, que no puntúan, y qué fuente falta (`AUTOMATIC` o
  `JUDGES`).
- `teamName` es el nombre actual de la inscripción del equipo.

Errores (mismo formato y tabla que el resto de la API): una categoría sin posiciones da `404` al
consultar, publicar o recalcular; generar dos veces, publicar con corridas o apelaciones pendientes y
publicar una revisión ya final dan `409 CONFLICT`. El `message` de una publicación bloqueada indica
las corridas pendientes o los identificadores de las apelaciones pendientes, pero su texto no es
estable.

## Ejecutar el frontend

El frontend (React + Vite) vive en [`frontend/`](frontend/README.md). Con el backend en
`localhost:8080`:

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173
npm test
```

En desarrollo Vite reenvía `/teams`, `/rounds` y `/competitions` al backend mediante un proxy, así que no hace falta CORS.

## Ejecutar la demo repetible

La demo conserva el recorrido completo existente y no se carga al iniciar Spring Boot:

```bash
mvn -pl backend -am package exec:java -DskipTests
```

Usa datos fijos para inscripción, programación, resultados, puntajes, posiciones y apelaciones. Su
comportamiento se verifica en `DemoScenarioTest`.

Las decisiones vigentes están documentadas en [DESIGN.md](DESIGN.md) y la evolución prevista de la
aplicación web en [architecture.md](architecture.md).
