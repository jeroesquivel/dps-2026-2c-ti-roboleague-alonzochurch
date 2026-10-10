# Spec: Desafío mixto (medición automática + panel de jueces)

| | |
|---|---|
| **Status** | Draft |
| **Author** | jeroesquivel |
| **Date** | 2026-10-10 |

## 1. Context

Se incorpora un tipo de desafío cuyo puntaje combina dos fuentes: **mediciones capturadas
automáticamente** (cronometraje, sensores de la pista) y **evaluaciones de un panel de jueces**
(creatividad, ejecución, diseño). Las dos fuentes no llegan juntas: el sistema de la pista entrega
sus mediciones al terminar la corrida, y el panel entrega sus notas cuando termina de deliberar,
minutos u horas después, en cualquier orden. El puntaje sólo tiene sentido con las dos: una corrida con
tiempo y sin notas no vale "las notas en cero". Lo usan los operadores de captura y los jueces, que
registran cada fuente por separado; los organizadores, que necesitan saber qué corridas esperan datos
antes de publicar, y los equipos, que necesitan ver qué aportó cada fuente a su puntaje.

Hoy el sistema funciona así:

- Una corrida se registra en **una sola operación**: `CaptureRunResult.Command` trae mediciones,
  evaluaciones e incidentes juntos, y `RunResult.capture` valida todo y crea el agregado
  (`DESIGN.md` 4.3). No existe una corrida "a medio capturar".
- Combinar criterios ya es posible: el desafío `RESCUE` del demo mezcla tiempo, objetivos, energía y
  la nota de jueces `DESIGN` en un mismo `ChallengeSpec`. Lo nuevo no es la fórmula combinada, sino
  que sus datos lleguen por separado y que el puntaje espere a ambos.
- La ausencia de datos puntúa cero: `JudgePanelScoringRule` sin evaluaciones emite "no evaluations
  recorded" con 0 puntos, y es el contrato común de todas las reglas (`DESIGN.md` 2.1.1). Por eso hoy
  una corrida sin notas de jueces **no se distingue** de una con notas en cero.
- Los criterios de jueces son métricas `MetricKind.JUDGE_CRITERION` del desafío, pero sus valores
  viajan como `JudgeEvaluations`, no en el `MeasurementSet`. `ChallengeSpec.validate(measurements)`
  exige igual una medición para toda métrica `REQUIRED`, también las `JUDGE_CRITERION`; el demo lo
  esquiva declarando `DESIGN` como opcional.
- `ChallengeSpec.validateEvaluations` verifica que cada criterio sea `JUDGE_CRITERION` y que cada
  juez pertenezca al heat (`Heat.judges()`), pero **no** que el panel esté completo.
- `RoundResults.requireUnusedAttempt` y el contrato de `RunResultRepository.save` rechazan una
  segunda corrida para la misma ronda, equipo e intento (`DESIGN.md` 4.12). Una segunda fuente para
  el mismo intento hoy sería un conflicto.
- `RunStatus` sólo distingue `CAPTURED` y `CORRECTED`. `CalculateRunScore` siempre devuelve un
  `ScoreBreakdown`, y `ScoreContribution` se clasifica por `ContributionKind` (`EARNED`, `BONUS`,
  `PENALTY`), no por la fuente del dato.
- `CategoryScoringService` puntúa toda corrida guardada; `Appeal.file` mide el plazo de apelación
  desde `RunResult.capturedAt`; `PublishStandingsUseCase` sólo bloquea la publicación por apelaciones
  pendientes (`DESIGN.md` 3.3).
- Las correcciones por apelación cambian mediciones e incidentes, nunca evaluaciones de jueces
  (`DESIGN.md` 4.3).
- Relación con otras specs: `SPEC-mejores-n-de-m-rondas.md` y `SPEC_bonus_sum_global_limit.md`
  trabajan sobre el puntaje de una corrida ya calculado. Una corrida mixta pendiente no tiene puntaje,
  así que no participa en ellas hasta completarse (FR-11).

Código relevante: `domain/challenge/{ChallengeSpec, MetricKind, MetricDefinition, MeasurementSet}`,
`domain/scoring/{JudgeEvaluations, ScoringContext, ScoreBreakdown, ScoreContribution}`,
`domain/scoring/rule/JudgePanelScoringRule`, `domain/result/{RunResult, RunStatus, RoundResults,
RunResultRepository}`, `domain/schedule/Heat`, `domain/ranking/{CategoryScoringService, TeamRuns}`,
`domain/appeal/Appeal`, `application/usecase/{CaptureRunResultUseCase, CalculateRunScoreUseCase,
GenerateStandingsUseCase, PublishStandingsUseCase, AcceptAppealUseCase}`, `domain/audit/AuditAction`.

Glosario:

| Término | Significado en esta spec |
| --- | --- |
| Desafío mixto | Desafío del reglamento configurado para recibir su resultado desde las dos fuentes por separado. |
| Turno | El intento de un equipo en una ronda: `Heat` + `AttemptNumber`. Corresponde a un único `RunResult`. |
| Fuente automática | Las mediciones de métricas que no son `JUDGE_CRITERION` (tiempo, objetivos, precisión, recursos). |
| Fuente del panel | Las evaluaciones de los jueces asignados al heat sobre los criterios `JUDGE_CRITERION` del desafío. |
| Fuente completa | La fuente fue registrada y cumple FR-4 (automática) o FR-5 (panel). |
| Corrida pendiente | Corrida de un desafío mixto con al menos una fuente sin registrar. No tiene puntaje. |
| Corrida completa | Corrida de un desafío mixto con las dos fuentes completas. Tiene puntaje. |

## 2. Functional requirements

**FR-1 — Configuración del desafío mixto.** Un desafío del reglamento puede configurarse como mixto.
La configuración se valida al construir el desafío y se rechaza con `InvalidValueException` si:

- ninguna regla de puntaje pertenece a la fuente automática o ninguna pertenece al panel;
- alguna regla lee a la vez criterios `JUDGE_CRITERION` y métricas de otro tipo.

Una regla pertenece al panel si todas las métricas que declara en `referencedMetrics()` son
`JUDGE_CRITERION`, y a la fuente automática si no declara ninguna `JUDGE_CRITERION`.

**FR-2 — Configuración versionada.** Ser mixto forma parte de la versión del reglamento. Un mismo
desafío puede ser mixto en una versión y no en otra. Una corrida se registra y se puntúa según la
versión de su ronda (`DESIGN.md` 3.2).

**FR-3 — Registro por fuente.** En un desafío mixto, cada fuente se registra en una operación propia,
indicando ronda, equipo, intento y actor:

- la fuente automática trae las mediciones;
- la fuente del panel trae las evaluaciones y los incidentes. *(Pendiente de OQ-2.)*

Las dos operaciones se aceptan en cualquier orden. La primera que llega para un turno crea la corrida
pendiente; la segunda completa **esa misma corrida**, sin crear otra. Cada registro conserva su
momento de recepción y su actor.

Una operación que trae datos de la otra fuente (mediciones de un criterio `JUDGE_CRITERION`, o
evaluaciones junto con las mediciones) se rechaza con `RuleViolationException`. Registrar por
separado una fuente en un desafío que no es mixto también se rechaza con `RuleViolationException`.

**FR-4 — Fuente automática completa.** La fuente automática se acepta sólo si trae todas las métricas
`REQUIRED` del desafío que no son `JUDGE_CRITERION`, con las mismas validaciones que hoy hace la
captura (métrica desconocida, valor inválido para su `MetricKind`). Los criterios `JUDGE_CRITERION`
nunca se exigen como medición en un desafío mixto. Una fuente incompleta o inválida se rechaza y no
deja la corrida creada ni modificada.

**FR-5 — Fuente del panel completa.** La fuente del panel se acepta sólo si contiene una evaluación de
**cada juez asignado al heat** para **cada criterio `JUDGE_CRITERION`** que leen las reglas del
desafío, con las validaciones actuales (criterio inexistente, juez fuera del heat, juez que evalúa dos
veces el mismo criterio). Una nota de 0 es una evaluación válida y cuenta para la completitud. Una
fuente incompleta se rechaza con `RuleViolationException`, indicando qué juez y qué criterio faltan,
y no deja la corrida creada ni modificada. *(Pendiente de OQ-1.)*

**FR-6 — Una vez por fuente.** Cada fuente se registra una sola vez por turno. Un segundo registro de
la misma fuente se rechaza con `ConflictException` y la corrida conserva los datos originales. Los
cambios posteriores sólo llegan por apelación (FR-14).

**FR-7 — Reglas de captura vigentes.** Las dos operaciones respetan las reglas actuales de la
captura: el equipo tiene turno en la ronda, el intento está dentro del `AttemptLimit` del desafío y la
categoría no tiene posiciones `FINAL` (`StandingsHistory.requireOpenForResults`).

**FR-8 — Estado visible.** Toda corrida de un desafío mixto expone su estado de completitud:

- `PENDING`, con las fuentes que faltan (`AUTOMATIC`, `JUDGES`) y las ya recibidas con su momento de
  recepción;
- `COMPLETE`, con el momento en que se completó (la recepción de la segunda fuente).

El estado se ve al consultar la corrida (`FindRunResult`) y al pedir su puntaje (`CalculateRunScore`).
Para una corrida pendiente, `CalculateRunScore` **no devuelve un total ni un desglose**: informa que
el puntaje está pendiente y qué fuente falta. Nunca lo presenta como cero. El estado de correcciones
(`CAPTURED` / `CORRECTED`) sigue existiendo con su significado actual.

**FR-9 — Fórmula combinada.** El puntaje de una corrida completa es:

```
total = Σ contribuciones de las reglas de la fuente automática
      + Σ contribuciones de las reglas del panel
      + Σ deducciones de los incidentes informados
```

Cada regla calcula como hoy: los pesos están en la configuración de cada regla (`PointsRate`,
`PointsCap`). *(Pendiente de OQ-3.)* El resultado no depende del orden en que llegaron las fuentes.

**FR-10 — Explicación por fuente.** El desglose de una corrida completa agrupa sus contribuciones por
fuente. Cada grupo indica la fuente, quién la registró, cuándo, sus contribuciones (con código,
`ContributionKind`, explicación y puntos, como hoy) y su subtotal. Las deducciones por incidentes
aparecen en el grupo de la fuente que los informó. *(Pendiente de OQ-2.)* Una contribución que no
deriva de una sola fuente (por ejemplo `BONUS_CAP`, si se implementa
`SPEC_bonus_sum_global_limit.md`) aparece en un grupo aparte del desafío. La suma de los subtotales
es igual al total, y `ScoreBreakdown.totalOf(kind)` sigue sumando por tipo sin importar el grupo.
El texto exacto de las explicaciones no es parte del contrato; la fuente, el subtotal y los puntos de
cada contribución sí.

Ejemplo (desafío `SHOWCASE`, panel J1 y J2):

| Fuente | Código | Tipo | Explicación | Puntos |
| --- | --- | --- | --- | --- |
| Automática | `TIME` | EARNED | 70 s against a reference of 90.000 s | 10.00 |
| Automática | `PRECISION` | EARNED | precision ratio 0.80 over a maximum of 30.00 points | 24.00 |
| **Subtotal automática** | | | | **34.00** |
| Panel | `JUDGES` | EARNED | average of 2 evaluations for CREATIVITY is 7.00 weighted by 2.00 | 14.00 |
| Panel | `JUDGES` | EARNED | average of 2 evaluations for EXECUTION is 8.00 weighted by 1.50 | 12.00 |
| Panel | `PENALTIES` | PENALTY | manual restart applied 1 time(s) | -3.00 |
| **Subtotal panel** | | | | **23.00** |
| **Total** | | | | **57.00** |

**FR-11 — Posiciones con corridas pendientes.** Al generar o recalcular posiciones, las corridas
pendientes no cuentan: no entran en la `AttemptAggregation`, ni en la selección de rondas, ni en los
desempates. La revisión de la tabla lista las corridas pendientes de la categoría (corrida, equipo,
ronda, intento y fuente faltante), también las de equipos que todavía no tienen ninguna corrida
completa. Completar una corrida no recalcula la tabla automáticamente: lo hace el recálculo
siguiente, como con cualquier corrida nueva. *(Pendiente de OQ-4.)*

**FR-12 — Publicación bloqueada.** No se pueden publicar posiciones `FINAL` mientras la categoría
tenga alguna corrida pendiente. La publicación se rechaza con `ConflictException`, con un mensaje que
indica cuántas corridas esperan qué fuente, igual que con las apelaciones pendientes.

**FR-13 — Apelaciones.** Una corrida pendiente no se puede apelar (`RuleViolationException`): no hay
puntaje que reclamar. En una corrida mixta, el plazo de apelación (`AppealWindow`) se cuenta desde que
se completó, no desde la recepción de la primera fuente.

**FR-14 — Correcciones.** Una apelación aceptada sobre una corrida mixta corrige mediciones e
incidentes como hoy. Las evaluaciones del panel se conservan como fueron registradas
(`DESIGN.md` 4.3). El puntaje recalculado combina las mediciones corregidas con las evaluaciones
originales, y la explicación sigue separando las fuentes.

**FR-15 — Auditoría.** Cada registro de fuente deja un evento de auditoría sobre la corrida con la
fuente, el actor, los datos recibidos y la versión del reglamento. La recepción de la segunda fuente
deja además constancia de que la corrida quedó completa. Un registro rechazado no deja evento.

**FR-16 — Desafíos no mixtos sin cambios.** En los desafíos que no son mixtos, la captura sigue
siendo una sola operación con mediciones, evaluaciones e incidentes. Su validación, su puntaje (una
nota de jueces ausente sigue puntuando cero), su desglose y sus posiciones son idénticos a los
actuales.

## 3. Constraints (non-functional)

**C-1 — Reglas de puntaje intactas.** No se modifica la interfaz `ScoringRule` ni ninguna de sus
implementaciones: el panel se puntúa con `JudgePanelScoringRule` y la fuente automática con las
reglas existentes. No se agrega a `ScoringRule` un método para declarar su fuente (`DESIGN.md` 5.9):
la fuente de una regla se deduce de `referencedMetrics()` y del `MetricKind` de esas métricas en el
desafío. `ScoringRulesTest.everyRule()` pasa sin cambios.

**C-2 — La espera se decide en el dominio.** Qué falta para completar una corrida, si una fuente está
completa y si una corrida puede puntuarse lo deciden el desafío y la corrida, no los casos de uso, que
sólo cargan, invocan, persisten y auditan (`DESIGN.md` 4.12). La exclusión de pendientes al rankear
vive en `ranking`, sin que `challenge` pase a depender de `ranking`.

**C-3 — Inmutabilidad.** Registrar la segunda fuente devuelve una corrida nueva y completa, sin
modificar la pendiente (`DESIGN.md` 4.11). La configuración mixta es un valor inmutable del desafío.

**C-4 — Unicidad y concurrencia.** Se conserva la garantía de "una corrida por ronda, equipo e
intento" del contrato de `RunResultRepository`. Si las dos fuentes de un turno llegan al mismo tiempo,
ninguna se pierde en silencio: la segunda completa la corrida creada por la primera, o falla con
`ConflictException` y puede reintentarse. `RunResultRepositoryContractTest` cubre el caso.

**C-5 — Determinismo y precisión.** Con los mismos datos y la misma versión del reglamento, el
desglose es idéntico sea cual sea el orden de llegada de las fuentes. Los grupos y sus contribuciones
aparecen siempre en el mismo orden: el de las reglas del desafío. El cálculo usa `Points` (escala 2,
`HALF_UP`).

**C-6 — Compatibilidad.** Los constructores actuales de `ChallengeSpec`, `RunResult` y
`CaptureRunResult.Command` siguen existiendo y equivalen a "desafío no mixto". El demo, los fixtures y
los tests existentes compilan y pasan sin cambiar sus expectativas.

**C-7 — Documentación.** `DESIGN.md` documenta la decisión: una sección nueva sobre el desafío mixto y
la actualización de 2.1.1 (ausencia de datos frente a espera), 2.3, 3.3 (publicación bloqueada),
4.3, 4.10 y 4.12 (nuevas reglas de captura), y las tablas de las secciones 6, 8 y 9.

## 4. Acceptance criteria

Trazabilidad con los criterios del enunciado: **CA-1** (acepta resultados de dos fuentes para el mismo
turno) → AC-1 a AC-7; **CA-2** (el puntaje queda pendiente y ese estado es visible) → AC-8 a AC-13;
**CA-3** (la fórmula combina ambas contribuciones y la explicación las separa) → AC-14 a AC-17. Los
demás cubren configuración, versionado y compatibilidad.

Salvo que se indique otra cosa, los criterios usan el desafío mixto `SHOWCASE` del ejemplo de FR-10,
con `AttemptLimit` 2:

- automática: `TIME` (`TIME_SECONDS`, requerida) con `TimeScoringRule` (referencia 90 s, 0.50 por
  segundo, tope 20) y `ACCURACY` (`PRECISION_RATIO`, requerida) con `PrecisionScoringRule` (máximo 30);
- panel: `CREATIVITY` con `JudgePanelScoringRule` peso 2 y `EXECUTION` con peso 1.50;
- penalización `RESTART` de 3 puntos;
- un heat con los jueces J1 y J2;
- datos de la corrida: `TIME` 70, `ACCURACY` 0.80; J1 (8, 9) y J2 (6, 7) en (`CREATIVITY`,
  `EXECUTION`); un incidente `RESTART`.

**AC-1 — Primero la medición automática.**
Given un turno sin datos,
When se registra la fuente automática y después la del panel,
Then existe una única corrida para ese turno, con las mediciones y las evaluaciones registradas;
and su estado es `COMPLETE`.

**AC-2 — Primero el panel.**
Given un turno sin datos,
When se registra la fuente del panel y después la automática,
Then existe una única corrida para ese turno y su estado es `COMPLETE`.

**AC-3 — Fuente repetida.**
Given una corrida con la fuente automática registrada (`TIME` 70),
When se vuelve a registrar la fuente automática con `TIME` 60,
Then se lanza `ConflictException`;
and la corrida conserva `TIME` 70.

**AC-4 — Datos de la otra fuente.**
Given un turno sin datos,
When se registra la fuente automática con una medición de `CREATIVITY`,
Then se lanza `RuleViolationException` y no se crea la corrida.

**AC-5 — Validaciones actuales en cada fuente.**
Given un turno sin datos,
When se registra la fuente automática sin `ACCURACY`, o la del panel con una evaluación de un juez J3
que no está en el heat,
Then se lanza `RuleViolationException` y no se crea la corrida.

**AC-6 — Límite de intentos y posiciones definitivas.**
Given un desafío con `AttemptLimit` 2,
When se registra cualquiera de las dos fuentes para el intento 3,
Then se lanza `RuleViolationException`;
and si la categoría tiene posiciones `FINAL`, registrar cualquiera de las dos fuentes lanza
`ConflictException`.

**AC-7 — Desafío no mixto.**
Given el desafío `RESCUE` del demo, que no es mixto,
When se registra por separado una fuente automática,
Then se lanza `RuleViolationException`;
and la captura en una sola operación sigue funcionando como hoy.

**AC-8 — Pendiente del panel.**
Given una corrida con sólo la fuente automática registrada,
When se consulta con `FindRunResult` y con `CalculateRunScore`,
Then el estado es `PENDING` y la fuente faltante es `JUDGES`;
and `CalculateRunScore` no informa un total ni un desglose.

**AC-9 — Pendiente de la medición.**
Given una corrida con sólo la fuente del panel registrada,
When se consulta,
Then el estado es `PENDING` y la fuente faltante es `AUTOMATIC`.

**AC-10 — Panel incompleto.**
Given una corrida con la fuente automática registrada,
When se registra la fuente del panel sin la evaluación de J2 para `EXECUTION`,
Then se lanza `RuleViolationException`, con un mensaje que nombra a J2 y `EXECUTION`;
and la corrida sigue `PENDING` de `JUDGES`.

**AC-11 — Notas en cero no son pendientes.**
Given una corrida con la fuente automática registrada,
When el panel registra todas sus evaluaciones con nota 0,
Then la corrida queda `COMPLETE` y el subtotal del panel es 0.

**AC-12 — Posiciones con corridas pendientes.**
Given un equipo A con una corrida completa de 57 y otra pendiente, y un equipo B con sólo una corrida
pendiente,
When se generan las posiciones,
Then A figura con 57, calculado sólo con su corrida completa, y B no figura en el ranking;
and la revisión lista las dos corridas pendientes con su equipo y la fuente faltante.

**AC-13 — Publicación bloqueada y apelación.**
Given una categoría con una corrida pendiente,
When se intenta publicar,
Then se lanza `ConflictException` y la revisión sigue `PROVISIONAL`;
and apelar la corrida pendiente lanza `RuleViolationException`;
and después de completarla y recalcular, la publicación es aceptada.

**AC-14 — Fórmula y explicación por fuente.**
Given la corrida del ejemplo con ambas fuentes registradas,
When se consulta su puntaje con `CalculateRunScore`,
Then el total es 57.00;
and el grupo de la fuente automática contiene `TIME` 10.00 y `PRECISION` 24.00, con subtotal 34.00;
and el grupo del panel contiene `JUDGES` 14.00 y 12.00 y `PENALTIES` -3.00, con subtotal 23.00;
and cada grupo indica el actor y el momento de recepción de su fuente;
and `totalOf(EARNED)` es 60.00 y `totalOf(PENALTY)` es -3.00.

**AC-15 — El orden de llegada no cambia el resultado.**
Given los mismos datos registrados en dos turnos distintos, uno en el orden automática → panel y otro
en el orden panel → automática,
When se consultan sus puntajes,
Then los dos desgloses son idénticos: mismos grupos, mismas contribuciones en el mismo orden, mismo
total.

**AC-16 — Corrección por apelación.**
Given la corrida de AC-14, completada, y posiciones generadas,
When se acepta dentro del plazo una apelación que corrige `TIME` a 80,
Then el subtotal automático pasa a 29.00 (`TIME` 5.00), el del panel sigue en 23.00 y el total es
52.00;
and la revisión anterior de la tabla conserva 57.00.

**AC-17 — Plazo de apelación desde la completitud.**
Given un reglamento con `AppealWindow` de 1 hora, una fuente automática recibida a las 10:00 y la del
panel a las 11:30,
When el equipo apela a las 12:15,
Then la apelación se acepta para revisión (está dentro de la hora posterior a las 11:30);
and una apelación a las 12:31 se rechaza por fuera de plazo.

**AC-18 — Configuración inválida.**
Given un desafío mixto sin ninguna regla del panel, o sin ninguna regla de la fuente automática, o con
una regla de test cuyo `referencedMetrics()` incluye `TIME` y `CREATIVITY`,
When se construye el desafío,
Then se lanza `InvalidValueException`.

**AC-19 — Versiones.**
Given un reglamento `v1` donde `SHOWCASE` no es mixto y una versión `v2` donde sí lo es,
When se captura en una ronda programada con `v1`,
Then se usa la captura en una sola operación;
and en una ronda programada con `v2` se exigen las dos fuentes por separado.

**AC-20 — Auditoría.**
Given la secuencia de AC-1,
When se consulta la auditoría de la corrida,
Then hay un evento por cada fuente, con su actor y sus datos, y la constancia de que la corrida quedó
completa;
and los registros rechazados de AC-3 a AC-5 no dejaron eventos.

**AC-21 — Sin desafíos mixtos, todo igual.**
Given el demo y los fixtures existentes, ninguno con desafíos mixtos,
When se ejecuta la suite de tests existente,
Then todos los tests pasan sin modificar sus expectativas, incluido `ScoringRulesTest`.

## 5. Out of scope

- Más de dos fuentes, o fuentes distintas de "automática" y "panel".
- Registrar una fuente en partes (el tiempo ahora y los sensores después, o juez por juez), salvo que
  OQ-1 decida lo contrario.
- Vencimiento, anulación o reemplazo de una corrida que nunca se completa.
- Corregir evaluaciones de jueces por apelación (hoy tampoco es posible, `DESIGN.md` 4.3).
- Recalcular posiciones automáticamente al completarse una corrida.
- Ponderar las fuentes según el orden o el momento de llegada.
- El adaptador hacia el sistema de cronometraje o sensores (el puerto de salida que menciona
  `DESIGN.md` 8), API REST, persistencia real o formato de presentación de la explicación.

## 6. Open questions

**OQ-1 — ¿El panel entrega sus notas en bloque o juez por juez, y se admite un panel incompleto?**
Esta spec propone **un único registro del panel** con las notas de todos los jueces del heat para
todos los criterios (FR-5). En la práctica, cada juez podría cargar sus notas por separado, y la
fuente quedaría completa cuando llegue la última. También hay que decidir qué pasa si un juez
asignado no puede evaluar: hoy la corrida quedaría pendiente para siempre. Un quórum (por ejemplo,
2 de 3 jueces) exige configurarlo en el reglamento y explicar en el desglose cuántos jueces
promediaron. Afecta FR-5, FR-6, AC-10 y la forma de la operación de registro del panel.

**OQ-2 — ¿Qué fuente informa los incidentes que generan penalizaciones?** Esta spec propone que
lleguen con el **panel**, porque hoy un reinicio manual o una salida de pista los observa una
persona. Si alguno lo detectan los sensores, como una salida de pista, los incidentes podrían llegar
con la fuente automática o con ambas. En ese caso hay que decidir si se unen o si un mismo código
puede informarse dos veces. Cambia qué datos trae cada operación (FR-3), en qué grupo aparece cada
deducción (FR-10) y el subtotal de cada fuente en AC-14.

**OQ-3 — ¿El reglamento pondera cada fuente con un peso propio?** Esta spec propone **sumar** las
contribuciones de ambas fuentes. El peso relativo ya se expresa en la configuración de cada regla
(`PointsRate`, `PointsCap`). "La fórmula combina ambas contribuciones" también puede leerse como una
ponderación explícita del desafío (por ejemplo, 60 % medición y 40 % jueces) sobre los subtotales. En
ese caso, la configuración mixta suma dos pesos versionados, el desglose agrega la ponderación de cada
grupo como contribución explicada (nunca como ajuste posterior al total, `DESIGN.md` 2.4) y cambian
FR-9, FR-10 y los valores de AC-14 y AC-16.

**OQ-4 — ¿Se pueden generar posiciones provisionales mientras hay corridas pendientes?** Esta spec
propone **permitirlo**: las pendientes no cuentan y se listan en la revisión (FR-11), y sólo se bloquea
la publicación `FINAL` (FR-12). La alternativa es rechazar también la generación y el recálculo con
corridas pendientes. Es más simple y evita una tabla provisional que cambie cuando lleguen las notas,
pero un panel demorado bloquearía toda la categoría, y una apelación aceptada sobre otra corrida no
podría recalcular. Afecta FR-11, AC-12 y el recálculo que dispara `AcceptAppealUseCase`.
