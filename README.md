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
backend/    Spring Boot, composición, adaptadores en memoria y API HTTP
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

## API: captura de resultados

Spec: [`.docs/specs/SPEC_capturar-resultados.md`](.docs/specs/SPEC_capturar-resultados.md). `RunController`
invoca los puertos `CaptureRunResult`, `RegisterAutomaticMeasurements`, `RegisterPanelEvaluations` y
`FindRunResult`. Un desafío común se captura en una operación; uno mixto (F3) recibe sus dos fuentes por
separado y en cualquier orden.

| Operación | Ruta | Respuesta |
| --- | --- | --- |
| Captura completa (desafío no mixto) | `POST /runs` | `201` + `Location: /runs/{runId}` |
| Fuente automática (desafío mixto) | `POST /runs/automatic-measurements` | `201` si abre la corrida, `200` si la completa |
| Fuente de jueces (desafío mixto) | `POST /runs/panel-evaluations` | `201` si abre la corrida, `200` si la completa |
| Consultar una corrida | `GET /runs/{runId}` | `200` |

Todas devuelven el mismo cuerpo que `GET /runs/{runId}` (ver [API: apelaciones y
corridas](#api-apelaciones-y-corridas)), con `evaluations` además de mediciones e incidentes. Las dos
fuentes de un mismo equipo, ronda e intento operan sobre el mismo `runId`.

```json
// POST /runs
{"roundId": "ROUND-1", "teamId": "TEAM-1", "attemptNumber": 1, "actor": "scorekeeper",
 "measurements": {"TIME": 70, "OBJECTIVES": 5, "ENERGY": 40},
 "evaluations": [{"judgeId": "J1", "criterion": "DESIGN", "score": 8}],
 "incidents": [{"code": "RESTART", "occurrences": 1}]}

// POST /runs/automatic-measurements
{"roundId": "ROUND-2", "teamId": "TEAM-1", "attemptNumber": 1, "actor": "track-system",
 "measurements": {"TIME": 70, "ACCURACY": 0.8}}

// POST /runs/panel-evaluations
{"roundId": "ROUND-2", "teamId": "TEAM-1", "attemptNumber": 1, "actor": "head-judge",
 "evaluations": [{"judgeId": "J1", "criterion": "CREATIVITY", "score": 8}],
 "incidents": []}
```

- Las métricas se indican por clave y las evaluaciones por juez y criterio; no se serializa
  `MeasurementSet`, `JudgeEvaluations` ni `RunResult`. `evaluations` e `incidents` pueden ser `[]` pero
  no faltar.
- Una corrida pendiente tiene `completion.status = "PENDING"` y `completion.missingSources` con la
  fuente que falta (`AUTOMATIC` o `JUDGES`); no tiene puntaje (`GET /runs/{runId}/score` responde
  `PENDING`). Cada recibo de `completion.receipts` trae sólo `source`, `actor` y `receivedAt`; la
  comprobación interna de la fuente no forma parte del contrato.
- Una fuente repetida, un intento ya capturado y la captura de una categoría con posiciones finales dan
  `409 CONFLICT` y no guardan nada (la recepción original se conserva). Un intento por encima del
  máximo del desafío, una métrica o penalidad inexistente, un juez fuera del heat, un panel incompleto,
  un equipo ajeno a la ronda y usar el modo equivocado (fuente suelta en un desafío común o captura
  completa en uno mixto) dan `422 RULE_VIOLATION`. Campos faltantes o valores inválidos dan
  `400 INVALID_VALUE`; una ronda o corrida inexistente, `404`.

## API: apelaciones y corridas

Spec: [`.docs/specs/SPEC_gestionar-apelaciones.md`](.docs/specs/SPEC_gestionar-apelaciones.md). Los
controllers invocan los puertos `SubmitAppeal`, `FindAppeal`, `AcceptAppeal`, `RejectAppeal`,
`FindRunResult` y `CalculateRunScore`. Aceptar y rechazar son recursos distintos (no hay un endpoint
con un indicador que combine ambas decisiones) y la corrección de una corrida entra sólo por
`AcceptAppeal`, que valida contra el reglamento fijado en la corrida y recalcula las posiciones si ya
existen.

| Operación | Ruta | Cuerpo | Respuesta |
| --- | --- | --- | --- |
| Presentar una apelación | `POST /appeals` | `{"runId", "teamId", "claim", "actor"}` | `201` `{"appealId"}` y `Location: /appeals/{appealId}` |
| Consultar una apelación | `GET /appeals/{appealId}` | — | `200` con la apelación |
| Aceptar | `POST /appeals/{appealId}/acceptance` | `{"rationale", "correctedMeasurements", "correctedIncidents", "reviewer"}` | `200` con la apelación `ACCEPTED` |
| Rechazar | `POST /appeals/{appealId}/rejection` | `{"rationale", "reviewer"}` | `200` con la apelación `REJECTED` |
| Consultar una corrida | `GET /runs/{runId}` | — | `200` con el detalle de la corrida |
| Consultar su puntaje | `GET /runs/{runId}/score` | — | `200` con el puntaje explicado |

Una apelación tiene esta forma; `decision` es `null` mientras `status` es `SUBMITTED`:

```json
{
  "appealId": "APPEAL-2",
  "runId": "RUN-1",
  "teamId": "TEAM-1",
  "claim": "timer late",
  "submittedAt": "2026-03-02T10:30:00.000Z",
  "status": "ACCEPTED",
  "decision": {"reviewer": "rev", "rationale": "video review", "decidedAt": "2026-03-02T11:00:00.000Z"}
}
```

Para aceptar se envía el **conjunto completo** de mediciones e incidentes que reemplaza al resultado
vigente, no diferencias; `correctedIncidents` puede ser `[]` pero no puede faltar:

```json
{
  "rationale": "video review",
  "correctedMeasurements": {"TIME": 80, "OBJECTIVES": 5, "ENERGY": 48},
  "correctedIncidents": [{"code": "RESTART", "occurrences": 1}],
  "reviewer": "rev"
}
```

El detalle de una corrida (`GET /runs/{runId}`) trae `status` (`CAPTURED` o `CORRECTED`),
`currentMeasurements`/`currentIncidents`, `originalMeasurements`/`originalIncidents` (el original se
conserva siempre), `corrections` (cada una con `appliedAt`, `actor`, `reason`, sus mediciones e
incidentes y la apelación de origen), `rulebookVersion`, `roundId`, `heatId`, `attemptNumber` y
`evaluations` (las del panel, tal como se recibieron) y `completion` (`PENDING` o `COMPLETE`, fuentes esperadas, faltantes y recibidas). El puntaje se describe en [API: puntaje de una corrida](#api-puntaje-de-una-corrida).

Errores (mismo formato y tabla que el resto de la API): un equipo ajeno, una ventana vencida o una
corrida incompleta dan `422 RULE_VIOLATION` y no guardan ni auditan nada; apelar dos veces la misma
corrida (aunque la primera esté resuelta) y resolver una apelación ya resuelta dan `409 CONFLICT`; una
corrección que viola el reglamento histórico (métrica o penalidad inexistente, valor fuera de rango)
da `422` sin cambiar la apelación, la corrida, las posiciones ni la auditoría; campos ausentes o en
blanco, números negativos o incidentes sin ocurrencias dan `400 INVALID_VALUE`; apelaciones o corridas
inexistentes dan `404`. Con posiciones existentes, aceptar crea una revisión `PROVISIONAL` nueva
(`GET …/standings/revisions`); sin posiciones no se crea ninguna. Mientras haya una apelación
pendiente, publicar las posiciones de su categoría da `409`.

## API: puntaje de una corrida

Spec: [`.docs/specs/SPEC_consultar-puntaje-explicable.md`](.docs/specs/SPEC_consultar-puntaje-explicable.md).
`GET /runs/{runId}/score` invoca `FindRunResult` (estado de completitud) y `CalculateRunScore` (el único
cálculo del puntaje); no hay fórmulas en la API ni en el frontend. La corrida se puntúa con la versión
del reglamento fijada en ella, que la respuesta informa, y con sus valores vigentes si fue corregida.

```json
{
  "runId": "RUN-1",
  "teamId": "TEAM-1",
  "challengeId": "RESCUE",
  "rulebookVersion": "v1",
  "completionStatus": "COMPLETE",
  "missingSources": [],
  "total": 93.50,
  "contributions": [
    {"ruleCode": "TIME", "kind": "EARNED", "explanation": "99.000 s against a reference of 120.000 s", "points": 10.50},
    {"ruleCode": "BONUS", "kind": "BONUS", "explanation": "OBJECTIVES 5.000 is at least 5: bonus granted", "points": 15.00},
    {"ruleCode": "BONUS_CAP", "kind": "BONUS", "explanation": "bonuses obtained 35.00 exceed the cap of 25.00: 10.00 trimmed", "points": -10.00},
    {"ruleCode": "PENALTIES", "kind": "PENALTY", "explanation": "manual restart applied 1 time(s)", "points": -3.00}
  ],
  "bonusCap": {"obtained": 35.00, "cap": 25.00, "trimmed": 10.00, "applied": 25.00},
  "bySource": null
}
```

- `contributions` enumera cada contribución, incluidas las que aportan cero, y su suma es `total`.
  `kind` es `EARNED`, `BONUS` o `PENALTY`; las penalidades tienen puntos negativos.
- `bonusCap` está presente sólo si el desafío limita la suma de bonificaciones: `obtained` (antes del
  tope), `cap`, `trimmed` (el recorte, positivo) y `applied` (`obtained - trimmed`). El recorte también
  está en `contributions` como `BONUS_CAP`. Los números son parte del contrato; el texto de
  `explanation` no.
- `bySource` está presente sólo en desafíos mixtos: `sources` (`source` `AUTOMATIC` o `JUDGES`,
  `subtotal` y sus `contributions`) y `acrossSources` (ajustes que no derivan de una sola fuente,
  como `BONUS_CAP`). La suma de los subtotales más el de `acrossSources` es `total`. Las deducciones
  por incidentes aparecen en el grupo `JUDGES`.
- **Corrida pendiente.** Una corrida mixta que espera una fuente no tiene puntaje parcial: responde
  `200` con `"completionStatus": "PENDING"`, `"missingSources": ["JUDGES"]` (o `AUTOMATIC`) y `total`,
  `contributions`, `bonusCap` y `bySource` en `null`.
- Una corrida inexistente da `404 NOT_FOUND`.

## API: auditoría

Spec: [`.docs/specs/SPEC_consultar-auditoria.md`](.docs/specs/SPEC_consultar-auditoria.md).
`GET /audit?subjectId=<ID>` invoca `FindAuditTrail` y devuelve la traza del sujeto. Es de sólo lectura:
cualquier otro método responde `405`. El tipo de sujeto se deduce del prefijo del identificador:
`SEASON`, `COMPETITION`, `CATEGORY`, `TEAM`, `ROUND`, `RUN` o `APPEAL`.

```json
{
  "subjectId": "RUN-1",
  "events": [
    {"occurredAt": "2026-03-01T10:00:00.123456Z", "action": "RESULT_CAPTURED", "subjectId": "RUN-1",
     "actor": "dev-seed", "details": {"measurements": "{ENERGY=48, OBJECTIVES=5, TIME=99}", "rulebook": "v1"}},
    {"occurredAt": "2026-03-01T11:30:05.361953Z", "action": "RESULT_CORRECTED", "subjectId": "RUN-1",
     "actor": "reviewer", "details": {"corrected": "{ENERGY=48, OBJECTIVES=5, TIME=120}",
     "original": "{ENERGY=48, OBJECTIVES=5, TIME=99}", "reason": "appeal APPEAL-1 accepted"}}
  ]
}
```

- Los eventos van en orden de `occurredAt`; los del mismo instante conservan el orden en que se
  registraron. `occurredAt` es un instante ISO-8601 en UTC.
- `action` es una de: `SEASON_CREATED`, `COMPETITION_CREATED`, `RULEBOOK_PUBLISHED`, `TEAM_REGISTERED`,
  `TEAM_REJECTED`, `ROUND_SCHEDULED`, `RESULT_CAPTURED`, `RESULT_SOURCE_RECEIVED`, `RESULT_CORRECTED`,
  `STANDINGS_GENERATED`, `STANDINGS_PUBLISHED`, `STANDINGS_RECALCULATED`, `APPEAL_SUBMITTED`,
  `APPEAL_RESOLVED`.
- `details` sólo trae las claves registradas para esa acción, con valores de texto: `rulebook`,
  `version`, `violations`, `heats`, `measurements`, `source`, `evaluations`, `incidents`, `original`,
  `corrected`, `reason`, `revision`, `run`, `status` y `rationale`. Una corrección conserva el valor
  `original` y el `corrected`; la resolución de una apelación trae `status` (`ACCEPTED` o `REJECTED`) y
  `rationale`. Los textos de `measurements`, `original` y `corrected` son informativos (no son un
  formato para interpretar).
- Un identificador bien formado sin eventos, o inexistente, da `200` con `"events": []`. Un
  `subjectId` ausente o de un tipo no soportado da `400 INVALID_VALUE`.
- La traza de una apelación aceptada tiene `APPEAL_SUBMITTED` y `APPEAL_RESOLVED`; la corrida suma
  `RESULT_CORRECTED`. En una apelación rechazada no hay corrección.

## API: configuración del evento

Spec: [`.docs/specs/SPEC_configurar-evento.md`](.docs/specs/SPEC_configurar-evento.md). Cuatro endpoints
sobre los puertos `CreateSeason`, `CreateCompetition`, `FindCompetition` y `PublishRulebook`. El actor va
en el cuerpo de cada solicitud que escribe.

| Método y ruta | Resultado |
|---|---|
| `POST /seasons` | `201` `{"seasonId": "SEASON-1"}` |
| `POST /seasons/{seasonId}/competitions` | `201` + `Location: /competitions/{id}` |
| `GET /competitions/{competitionId}` | `200` con datos, categorías y versión activa |
| `POST /competitions/{competitionId}/rulebooks` | `201` `{"competitionId", "rulebookVersion": "v2"}` |

```bash
curl -s -X POST localhost:8080/seasons -H 'Content-Type: application/json' -d '{
  "name": "Temporada 2027", "year": 2027,
  "period": {"start": "2027-01-01", "end": "2027-12-31"}, "actor": "organizer"}'

curl -s -X POST localhost:8080/seasons/SEASON-1/competitions -H 'Content-Type: application/json' -d '{
  "name": "Copa Otoño", "period": {"start": "2027-04-10", "end": "2027-04-11"}, "actor": "organizer",
  "categories": [{"name": "Junior", "minimumAge": 10, "maximumAge": 14, "robotClass": "RESCUE_BOT"}],
  "rulebook": { ... ver abajo ... }}'
```

La respuesta de la segunda llamada conserva el orden de las categorías y repite su nombre:

```json
{"competitionId": "COMPETITION-1", "rulebookVersion": "v1",
 "categories": [{"categoryId": "CATEGORY-1", "name": "Junior"}]}
```

### Reglamento

```json
{
  "challenges": [{
    "id": "RESCUE", "name": "Rescue mission",
    "metrics": [{"key": "TIME", "kind": "TIME_SECONDS", "unit": "s", "requirement": "REQUIRED"}],
    "scoringRules": [
      {"type": "TIME", "metric": "TIME", "referenceSeconds": 120, "pointsPerSecondSaved": 0.5, "maximumPoints": 30}],
    "penalties": [{"code": "RESTART", "description": "manual restart", "pointsDeducted": 3}],
    "maximumAttempts": 2,
    "bestRounds": {"counted": 2, "outOf": 3},
    "bonusCap": {"maximumPoints": 20},
    "mixedSources": false
  }],
  "eligibility": [{"type": "AGE_RANGE"}, {"type": "ROBOT_CLASS"}],
  "attemptAggregation": {"type": "BEST_ATTEMPT"},
  "tiebreaks": [{"type": "HIGHEST_SINGLE_RUN"}, {"type": "FASTEST_METRIC", "metric": "TIME"}],
  "appealWindowMinutes": 60
}
```

`bestRounds`, `bonusCap` y `mixedSources` son opcionales (`null` o ausentes = no aplica). Las variantes son
DTO planos con un discriminador `type`; cada una usa sólo sus campos:

| Familia | `type` | Campos |
|---|---|---|
| puntaje | `TIME` | `metric`, `referenceSeconds`, `pointsPerSecondSaved`, `maximumPoints` |
| | `OBJECTIVES` | `metric`, `pointsPerObjective`, `maximumObjectives` |
| | `PRECISION` | `metric`, `maximumPoints` |
| | `RESOURCE` | `metric`, `allowance`, `pointsPerUnitOver` |
| | `JUDGES` | `criterion`, `weight` |
| | `BONUS` | `metric`, `comparison` (`AT_LEAST`/`AT_MOST`), `threshold`, `bonusPoints` |
| elegibilidad | `AGE_RANGE`, `ROBOT_CLASS` | — |
| | `TEAM_COMPOSITION` | `minimumCompetitors`, `maximumCompetitors`, `coachRequiredUnderAge` |
| | `ROBOT_SPECIFICATION` | `maximumWeightKg`, `maximumDimensionsMm` `{length, width, height}` |
| | `REQUIRED_DOCUMENTS` | `documents` (`PARENTAL_CONSENT`, `INSURANCE`, `TECHNICAL_SHEET`, `SAFETY_CHECK`) |
| agregación | `BEST_ATTEMPT`, `SUM_OF_ATTEMPTS` | — |
| desempate | `HIGHEST_SINGLE_RUN`, `FEWEST_PENALTIES` | — |
| | `FASTEST_METRIC` | `metric` |

Las métricas tienen `kind` `TIME_SECONDS`, `OBJECTIVE_COUNT`, `PRECISION_RATIO`, `RESOURCE_UNITS` o
`JUDGE_CRITERION`, y `requirement` `REQUIRED` u `OPTIONAL`. Un desafío mixto (F3) lleva
`"mixedSources": true` y reglas de ambas fuentes (automáticas y `JUDGES`).

### Versiones y errores

- Publicar un reglamento crea la versión siguiente (`v2`, `v3`, …) y la deja activa. Las rondas ya
  programadas conservan la versión con la que se programaron, así que no cambian sus resultados.
- Los errores usan el formato común (`code` y `message`):

| Caso | Respuesta |
|---|---|
| Temporada o competencia inexistente (no se guarda nada) | `404 NOT_FOUND` |
| JSON mal formado | `400 MALFORMED_REQUEST` |
| Campo faltante, `type` desconocido, valor inválido o desafío mixto sin reglas de ambas fuentes | `400 INVALID_VALUE` |
| Competencia sin categorías | `400 INVALID_VALUE` |
| Período de la competencia fuera de la temporada | `422 RULE_VIOLATION` |
| Temporada que se superpone con otra | `409 CONFLICT` |

Una solicitud inválida no deja nada a medias: el mapper arma el reglamento completo antes de invocar el
puerto, así que no se crea la competencia ni se consume un Id.

## Ejecutar el frontend

El frontend (React + Vite) vive en [`frontend/`](frontend/README.md). Con el backend en
`localhost:8080`:

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173
npm test
```

En desarrollo Vite reenvía `/teams`, `/rounds`, `/competitions`, `/appeals`, `/runs`, `/audit` y `/seasons` al backend mediante un proxy, así que no hace falta CORS.

## Ejecutar la demo repetible

La demo conserva el recorrido completo existente y no se carga al iniciar normalmente el backend:

```bash
mvn -pl backend -am package exec:java -DskipTests
```

Usa datos fijos para inscripción, programación, resultados, puntajes, posiciones y apelaciones. Su
comportamiento se verifica en `DemoScenarioTest`. `Main` inicia un contexto Spring sin servidor HTTP
y activa `DemoConfig`; la demo recibe los mismos puertos y adaptadores singleton del backend, sin un
composition root manual. La prueba usa el contexto Spring real y un reloj fijo.

Las decisiones vigentes están documentadas en [DESIGN.md](DESIGN.md) y la evolución prevista de la
aplicación web en [architecture.md](architecture.md).
