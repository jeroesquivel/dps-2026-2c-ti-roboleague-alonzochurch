# Spec: Mejores N de M rondas (F1)

| | |
|---|---|
| **Status** | Draft |
| **Author** | jeroesquivel |
| **Date** | 2026-10-10 |

## 1. Context

Algunas competencias pasan a contabilizar sólo los mejores resultados de cada equipo: por ejemplo, las
mejores tres de cinco rondas. El reglamento define N y M **por desafío**. Así, un mal resultado aislado
(una falla del robot, una pista en malas condiciones) no define la posición de un equipo constante.
Lo usan los organizadores, que configuran el reglamento, y los equipos y jueces, que necesitan entender
por qué un equipo quedó en su posición.

Hoy el sistema funciona así:

- El puntaje de **una corrida** lo calcula `ChallengeSpec.score` con sus `ScoringRule` y devuelve un
  `ScoreBreakdown` explicado (`DESIGN.md` 2.3).
- El total de **un equipo en la categoría** lo decide la `AttemptAggregation` del `Rulebook`
  (`BestAttempt` o `SumOfAttempts`). `TeamRuns.aggregatedPoints` la aplica sobre **todas** las
  corridas del equipo en **todas** las rondas de la categoría, sin distinguir rondas (`DESIGN.md` 3.5
  y 4.8).
- `ScoredRun` no sabe a qué ronda pertenece la corrida: `CategoryScoringService.collect` pierde ese
  dato al aplanar las corridas.
- `StandingEntry` guarda posición, total y desempates aplicados, pero **no explica cómo se llegó al
  total**.
- `ChallengeSpec` define métricas, reglas, penalizaciones y `AttemptLimit` (intentos **por ronda**),
  pero no dice cuántas rondas existen ni cuántas cuentan.
- `CompetitionSchedule.requireAvailableOrdinal` impide repetir un ordinal en una categoría, pero no
  limita la cantidad de rondas.

Código relevante: `domain/challenge/ChallengeSpec`, `domain/ranking/{AttemptAggregation, TeamRuns,
TeamScoreSummary, ScoredRun, StandingEntry, RankingService, CategoryScoringService}`,
`domain/schedule/CompetitionSchedule`, `application/usecase/{ScheduleRoundUseCase,
GenerateStandingsUseCase, RecalculateStandingsUseCase}`.

Glosario:

| Término | Significado en esta spec |
| --- | --- |
| Ronda | Un `Round` de la categoría, identificado por su `RoundOrdinal`. Tiene un único desafío. |
| Intento | Un `RunResult` de un equipo dentro de una ronda (`AttemptNumber`, limitado por `AttemptLimit`). |
| Puntaje de ronda | Los puntos de un equipo en una ronda (ver FR-4). |
| M | Cantidad de rondas que el reglamento prevé para el desafío en una categoría. |
| N | Cantidad de rondas, las de mayor puntaje, que cuentan para el total del equipo. |
| Ronda considerada / descartada | Ronda disputada por el equipo cuyo puntaje entra / no entra en el total. |

## 2. Functional requirements

**FR-1 — Configuración por desafío.** Un desafío del reglamento puede configurarse para contabilizar
las mejores N de M rondas. N y M son enteros con `1 ≤ N ≤ M`. Cualquier otro valor se rechaza al
configurar el desafío con `InvalidValueException`.

**FR-2 — Configuración versionada.** N y M forman parte de la versión del reglamento. Dos versiones
pueden tener N y M distintos para el mismo desafío. Una tabla se genera con los valores del
reglamento activo y se recalcula con los de la versión con la que fue generada.

**FR-3 — Límite de rondas al programar.** Si un desafío está configurado con "mejores N de M", no se
puede programar en una categoría una ronda más de ese desafío cuando ya hay M. La programación se
rechaza con `ConflictException`, con un mensaje que indica el desafío, la categoría y M, y la ronda no
se guarda. Para los desafíos sin esa configuración no hay límite. 

**FR-4 — Puntaje de ronda.** El puntaje de un equipo en una ronda es el resultado de aplicar la
política de intentos del reglamento (`AttemptAggregation`) a los intentos de esa ronda: con
`BestAttempt`, el mejor intento de la ronda; con `SumOfAttempts`, la suma de sus intentos.


**FR-5 — Selección de las N mejores.** Para cada equipo y cada desafío configurado:

1. Las rondas disputadas se ordenan por puntaje de ronda, de mayor a menor.
2. Si dos rondas empatan, va primero la de menor ordinal.
3. Las primeras `min(N, rondas disputadas)` se **consideran**; el resto se **descarta**.
4. El aporte del desafío al total es la suma de los puntajes de las rondas consideradas.

**FR-6 — Rondas no disputadas.** Una ronda que el equipo no disputó no tiene puntaje: no cuenta como
cero ni ocupa un lugar entre las N. Un equipo con menos de N rondas disputadas suma todas las que
disputó. Los puntajes negativos se tratan como cualquier otro: si están entre los N mejores,
cuentan.

**FR-7 — Total del equipo en la categoría.**

```
total = AttemptAggregation(corridas de desafíos SIN configuración N de M)
      + Σ por desafío CON configuración: suma de sus N mejores puntajes de ronda
```

**FR-8 — Desafíos sin configuración.** Si ningún desafío de la categoría está configurado con N de M,
el total de cada equipo y el orden de la tabla son idénticos a los actuales.

**FR-9 — Explicación del total.** Cada entrada de la tabla de posiciones incluye una explicación de su
total. Para cada desafío configurado, la explicación indica:

- la regla aplicada, con un código estable (`BEST_ROUNDS`) y una descripción ("las mejores 3 de 5
  rondas");
- por cada ronda disputada, su ordinal, su puntaje de ronda y su estado `COUNTED` o `DISCARDED`;
  para las descartadas, también el motivo (p. ej. "fuera de las 3 mejores" o "empate con la ronda 4,
  gana el menor ordinal");
- el subtotal que aporta el desafío.

Para las corridas de desafíos sin configuración, la explicación nombra la `AttemptAggregation`
aplicada (código y descripción) y su subtotal. La suma de los subtotales es igual al total de la
entrada.

Ejemplo (mejores 3 de 5, `BestAttempt`):

| Ronda | Intentos | Puntaje de ronda | Estado |
| --- | --- | --- | --- |
| 1 | 40, 55 | 55 | COUNTED |
| 2 | 30 | 30 | DISCARDED (fuera de las 3 mejores) |
| 3 | 70 | 70 | COUNTED |
| 4 | 45 | 45 | COUNTED |
| 5 | 45 | 45 | DISCARDED (empate con la ronda 4, gana el menor ordinal) |
| **Total** | | **170** | |

**FR-10 — Explicación por revisión.** La explicación se conserva con cada revisión de la tabla. Si
una corrección o una apelación aceptada provoca un recálculo, la nueva revisión puede considerar
otras rondas, y la revisión anterior conserva su explicación original.

**FR-11 — Puntaje de corrida sin cambios.** El puntaje y el desglose de una corrida individual
(`CalculateRunScore`) no cambian, cuente o no su ronda para el total.

**FR-12 — Desempates sin cambios.** Los desempates existentes (`HighestSingleRunTiebreak`,
`FewestPenaltiesTiebreak`, `FastestMetricTiebreak`) siguen evaluando **todas** las corridas del
equipo, incluidas las de rondas descartadas, como ya documenta `DESIGN.md` 3.5. 

## 3. Constraints (non-functional)

**C-1 — Reglas de puntaje intactas.** No se modifica ninguna `ScoringRule` (`TimeScoringRule`,
`ObjectiveScoringRule`, `PrecisionScoringRule`, `ResourceScoringRule`, `JudgePanelScoringRule`,
`ThresholdBonusRule`, `PenaltyScoringRule`), ni `ScoreBreakdown`, `ScoreContribution` o
`ScoringContext`. Tampoco cambian el contrato de `AttemptAggregation` ni sus implementaciones.

**C-2 — Compatibilidad.** El demo, los fixtures y los tests existentes compilan y pasan sin cambiar
sus expectativas. Los constructores actuales de `ChallengeSpec` y `StandingEntry` siguen existiendo:
equivalen a "sin configuración N de M" y a "explicación por defecto".

**C-3 — Determinismo.** Con las mismas corridas y la misma versión del reglamento, la selección de
rondas y la explicación son siempre iguales. Los empates se resuelven por ordinal, nunca por un orden
de iteración o por identificadores técnicos.

**C-4 — Dependencias del dominio.** La lógica de selección vive en el dominio. La configuración N/M
pertenece al desafío y no puede introducir una dependencia `challenge → ranking`, porque `ranking` ya
depende de `challenge`.

**C-5 — Inmutabilidad.** La configuración N/M y la explicación son valores inmutables, coherentes con
`DESIGN.md` 4.11.

**C-6 — Documentación.** `DESIGN.md` documenta la decisión: una sección nueva y la actualización de
3.5, 4.8, 4.10 y la tabla de evolución de la sección 8.

## 4. Acceptance criteria

Trazabilidad con los criterios del enunciado: **CA-1** (configurable N de M) → AC-1 a AC-3;
**CA-2** (explicación de rondas consideradas y descartadas) → AC-4 a AC-10; **CA-3** (sin modificar
reglas de puntaje) → AC-11 a AC-13.

**AC-1 — Configuración válida.**
Given un desafío configurado con N = 3 y M = 5,
When se publica el reglamento,
Then el desafío expone la configuración "mejores 3 de 5".

**AC-2 — Configuración inválida.**
Given un desafío con N = 0, M = 0 o N > M (p. ej. N = 4, M = 3),
When se intenta configurar,
Then se lanza `InvalidValueException`. N = M es válido.

**AC-3 — Límite de M rondas.**
Given un desafío "mejores 2 de 3" con 3 rondas ya programadas en una categoría,
When se programa una cuarta ronda de ese desafío en la misma categoría,
Then se lanza `ConflictException` y la ronda no se guarda;
and programar la tercera ronda era aceptado;
and un desafío sin configuración admite rondas sin límite.

**AC-4 — Mejores 3 de 5.**
Given un equipo con puntajes de ronda 55, 30, 70, 45 y 40 en un desafío "mejores 3 de 5",
When se generan las posiciones,
Then su total es 170 (70 + 55 + 45);
and la explicación marca las rondas 1, 3 y 4 como `COUNTED` y las rondas 2 y 5 como `DISCARDED`, con
su motivo.

**AC-5 — Empate en el corte.**
Given un desafío "mejores 3 de 5" donde las rondas 4 y 5 empatan en el tercer lugar,
When se generan las posiciones,
Then se considera la ronda 4 y se descarta la ronda 5, con motivo de empate.

**AC-6 — Menos rondas que N.**
Given un desafío "mejores 3 de 5" y un equipo que disputó sólo 2 rondas,
When se generan las posiciones,
Then ambas rondas son `COUNTED`, ninguna es `DISCARDED` y el total es su suma.

**AC-7 — Varios intentos en una ronda.**
Given un desafío "mejores 1 de 2" con intentos de 30 y 40 en la ronda 1 y de 60 en la ronda 2,
When se generan las posiciones con `BestAttempt`,
Then los puntajes de ronda son 40 y 60, y cuenta la ronda 2 (total 60);
and con `SumOfAttempts` los puntajes de ronda son 70 y 60, y cuenta la ronda 1 (total 70).

**AC-8 — La selección cambia el ganador.**
Given dos equipos en un desafío "mejores 2 de 3": A con 50, 50, 0 y B con 60, 30, 30,
When se generan las posiciones,
Then A queda primero con 100 y B segundo con 90, aunque con la suma de todas las rondas B tendría
120 y A 100.

**AC-9 — Desafío con y sin configuración en la misma categoría.**
Given una categoría con rondas de un desafío "mejores 2 de 3" y de otro desafío sin configuración,
When se generan las posiciones,
Then el total de cada equipo es la suma de ambos subtotales (FR-7);
and la explicación muestra un subtotal por cada parte, y la suma de los subtotales es igual al
total.

**AC-10 — Recálculo tras una apelación.**
Given posiciones generadas en las que la ronda 2 de un equipo fue `DISCARDED`,
When se acepta una apelación que sube el puntaje de esa ronda por encima de una ronda considerada y
se recalcula la tabla,
Then en la nueva revisión la ronda 2 es `COUNTED` y la ronda desplazada es `DISCARDED`;
and la revisión anterior conserva su explicación original.

**AC-11 — Versiones con N distinto.**
Given una tabla generada con un reglamento "mejores 2 de 3" y luego publicada una versión "mejores
3 de 3",
When se recalcula la tabla original,
Then se recalcula con N = 2.

**AC-12 — Sin configuración, todo igual.**
Given el demo y los fixtures existentes, ninguno con desafíos configurados con N de M,
When se ejecuta la suite de tests existente,
Then todos los tests pasan sin modificar sus expectativas.

**AC-13 — Puntaje de corrida y desempates intactos.**
Given una corrida de una ronda descartada,
When se consulta su puntaje con `CalculateRunScore`,
Then el desglose y el total son los mismos que sin la configuración N de M;
and `HighestSingleRunTiebreak` sigue considerando esa corrida.

## 5. Out of scope

- Otras políticas sobre rondas: promedio, descartar sólo la peor o ponderar rondas.
- Mostrar en la explicación las rondas aún no disputadas.
- Cambiar los desempates para que ignoren las rondas descartadas (ver OQ-2).
- API REST, persistencia real o formato de presentación de la explicación.
- Cambios en el cálculo del puntaje de una corrida o en las reglas de puntaje (C-1).

## 6. Open questions

No hay preguntas abiertas.