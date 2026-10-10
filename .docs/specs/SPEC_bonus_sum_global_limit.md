# Spec: Tope global de bonificaciones por desafío

| | |
|---|---|
| **Status** | Draft |
| **Author** | jeroesquivel |
| **Date** | 2026-10-10 |

## 1. Context

Un desafío puede otorgar varias bonificaciones independientes: por completar todos los objetivos,
por terminar rápido o por consumir poca energía. Sumadas, pueden pesar más que el desempeño
principal de la corrida. El reglamento incorpora un **tope**: la suma de las bonificaciones de un
desafío no puede superar un máximo configurable, sin importar cuántas bonificaciones individuales
se hayan obtenido. Lo usan los organizadores, que configuran el reglamento, y los equipos y jueces,
que necesitan ver por qué una bonificación ganada no sumó completa.

Hoy el sistema funciona así:

- `ThresholdBonusRule` es la única regla que emite bonificaciones: una contribución
  `ContributionKind.BONUS` con su `BonusPoints` si se cumple el umbral, o cero explicado si no
  (`DESIGN.md` 2.3 y 2.4). Un desafío puede declarar varias `ThresholdBonusRule`. Nada lo impide, y
  cada una suma por su cuenta, sin límite sobre el conjunto.
- `ContributionKind` clasifica cada contribución según lo que representa, sin depender de la regla
  que la produjo. `ScoreBreakdown.totalOf(BONUS)` ya suma las bonificaciones de una corrida
  (`DESIGN.md` 2.3.1).
- `ChallengeSpec.score` es el único punto que combina las contribuciones de las reglas con las
  deducciones del catálogo de penalizaciones (`DESIGN.md` 2.2).
- Los topes que existen (`PointsCap` en `TimeScoringRule` y `PrecisionScoringRule`) limitan lo que
  aporta **una** regla. Este tope es el primero que actúa sobre contribuciones de **varias** reglas.
- `DESIGN.md` 2.4 descarta aplicar ajustes como un descuento posterior al total, porque quedan fuera
  de la explicación. El recorte tiene que aparecer en el desglose.
- `ScoringRule` no tiene un método polimórfico para saber qué tipo de puntos emite una regla
  (`DESIGN.md` 5.9). El desafío sólo puede reconocer una bonificación por el `ContributionKind` de la
  contribución.
- Cada corrida se puntúa con la versión del reglamento de su ronda (`DESIGN.md` 3.2).
- Relación con `SPEC-mejores-n-de-m-rondas.md`: el tope actúa sobre el puntaje de una corrida. La
  agregación de intentos y la selección de rondas reciben ese puntaje ya recortado. Las dos
  funcionalidades son independientes.

Código relevante: `domain/challenge/ChallengeSpec`, `domain/scoring/{ScoreBreakdown,
ScoreContribution, ContributionKind, PointsCap, BonusPoints}`, `domain/scoring/rule/ThresholdBonusRule`,
`domain/ranking/{ScoredRun, TeamRuns, CategoryScoringService}`,
`application/usecase/CalculateRunScoreUseCase`, `demo/DemoRulebook`,
`test/.../support/RescueEditionFixture`.

Glosario:

| Término | Significado en esta spec |
| --- | --- |
| Bonificación | Toda contribución del desglose con `ContributionKind.BONUS`, sin importar qué regla la emitió. |
| Bonificaciones obtenidas | Suma de las bonificaciones individuales, antes de aplicar el tope. |
| Tope | Máximo configurado para la suma de bonificaciones de un desafío. |
| Bonificaciones aplicadas | Lo que efectivamente suman las bonificaciones al total: `min(obtenidas, tope)`. |
| Recorte | Diferencia entre obtenidas y aplicadas. Nunca es negativo. |

## 2. Functional requirements

**FR-1: Configuración por desafío.** Un desafío del reglamento puede configurarse con un tope de
bonificaciones, expresado como `PointsCap`. El tope es opcional. No admite valores negativos: se
rechazan con `InvalidValueException`. Un tope de cero es válido y significa que ninguna bonificación
suma. *(Pendiente de OQ-2.)*

**FR-2: Configuración versionada.** El tope forma parte de la versión del reglamento. Dos versiones
pueden tener topes distintos para el mismo desafío, o una tenerlo y la otra no. Una corrida se
puntúa, y se vuelve a puntuar al recalcular, con el tope de la versión de su ronda.

**FR-3: Qué cuenta como bonificación.** El tope abarca todas las contribuciones `BONUS` del desglose
de la corrida, las emita `ThresholdBonusRule` o cualquier otra `ScoringRule`. Las contribuciones
`EARNED` y `PENALTY` no entran en la suma ni se ven afectadas por el tope.

**FR-4: Tope sobre el conjunto.** Para cada corrida de un desafío con tope:

```
obtenidas = Σ contribuciones BONUS de las reglas
aplicadas = min(obtenidas, tope)
recorte   = obtenidas − aplicadas        (0 si obtenidas ≤ tope)
```

Ninguna bonificación individual se compara contra el tope. Una sola bonificación mayor que el tope
también se recorta, y varias bonificaciones menores que el tope se recortan si su suma lo supera.
*(Pendiente de OQ-1.)*

**FR-5: Recorte en el desglose.** Las contribuciones de cada bonificación conservan su valor y su
explicación originales. El desglose agrega una contribución propia del tope:

- con un código estable (`BONUS_CAP`);
- con `-recorte` puntos;
- de modo que `ScoreBreakdown.total()` incluya sólo las bonificaciones aplicadas y
  `totalOf(BONUS)` sea igual a las bonificaciones aplicadas;
- sin alterar `totalOf(EARNED)` ni `totalOf(PENALTY)`: el recorte no es una penalización.

**FR-6: Explicación del tope.** La explicación de la contribución `BONUS_CAP` indica las bonificaciones
obtenidas, el tope aplicado y el recorte resultante. Se emite siempre que el desafío tenga tope,
también cuando el recorte es cero, igual que las demás reglas emiten contribuciones de cero
(`DESIGN.md` 2.3). El texto exacto no es parte del contrato, pero los tres valores sí.

Ejemplo (tope 25):

| Código | Tipo | Explicación | Puntos |
| --- | --- | --- | --- |
| `OBJECTIVES` | EARNED | 5 of 5 objectives at 10 points each | 50.00 |
| `BONUS` | BONUS | OBJECTIVES 5 is at least 5: bonus granted | 15.00 |
| `BONUS` | BONUS | TIME 55 is at most 60: bonus granted | 10.00 |
| `BONUS` | BONUS | ENERGY 35 is at most 40: bonus granted | 10.00 |
| `BONUS_CAP` | BONUS | bonuses obtained 35.00 exceed the cap of 25.00: 10.00 trimmed | -10.00 |
| `PENALTIES` | PENALTY | manual restart applied 1 time(s) | -3.00 |
| **Total** | | | **72.00** |

**FR-7: Desafíos sin tope.** Si un desafío no tiene tope, su desglose no incluye la contribución
`BONUS_CAP`, y sus totales y el orden de la tabla son idénticos a los actuales.

**FR-8: Consumidores del puntaje.** Todo lo que usa el total de una corrida recibe el total ya
recortado: `CalculateRunScore`, `AttemptAggregation`, la tabla de posiciones y
`HighestSingleRunTiebreak`. `FewestPenaltiesTiebreak` no cambia, porque el recorte no es `PENALTY`.

**FR-9: Correcciones y apelaciones.** Si una corrección o una apelación aceptada cambia las
mediciones de una corrida, el tope se vuelve a aplicar sobre las bonificaciones que resulten. Las
revisiones anteriores de la tabla no cambian.

## 3. Constraints (non-functional)

**C-1: Reglas de bonificación intactas.** No se modifica `ThresholdBonusRule` ni ninguna otra
`ScoringRule`, ni la interfaz `ScoringRule`, `BonusPoints` o `ScoreContribution`. El tope no menciona
`ThresholdBonusRule` ni otra regla concreta: reconoce las bonificaciones sólo por su
`ContributionKind`.

**C-2: Sin métodos nuevos en `ScoringRule`.** No se agrega a la interfaz un método como
`isBonus()` o `code()` para identificar las reglas que bonifican (`DESIGN.md` 5.9).

**C-3: El recorte vive en el desglose.** El recorte es una contribución del mismo `ScoreBreakdown`, no
un descuento aplicado después sobre el total (`DESIGN.md` 2.4).

**C-4: Compatibilidad.** Los constructores actuales de `ChallengeSpec` y `RulebookDraft` siguen
existiendo y equivalen a "sin tope". El demo, los fixtures y los tests existentes compilan y pasan sin
cambiar sus expectativas, incluidos los tests de contrato de `ScoringRulesTest.everyRule()` y
`penaltyContributionsNeverAddPoints`.

**C-5: Determinismo y precisión.** El cálculo usa `Points` (escala 2, `HALF_UP`). Con las mismas
mediciones y la misma versión del reglamento, el desglose es siempre el mismo, con la contribución
`BONUS_CAP` siempre en la misma posición.

**C-6: Inmutabilidad.** El tope es un value object inmutable, coherente con `DESIGN.md` 4.11.

**C-7: Documentación.** `DESIGN.md` documenta la decisión: una fila nueva en la tabla de 2.1 (tope de
bonificaciones del desafío), la actualización de 2.2, 2.3.1 y 2.4, y una fila en la tabla de
evolución de la sección 8.

## 4. Acceptance criteria

Trazabilidad con los criterios del enunciado: **CA-1** (el tope se aplica sobre el conjunto y no sobre
cada bonificación) → AC-1 a AC-6; **CA-2** (la explicación muestra bonificaciones, tope y recorte) →
AC-7 a AC-9; **CA-3** (sin reescribir las reglas de bonificación) → AC-10 a AC-12. Los demás cubren
compatibilidad, versionado y recálculo.

Salvo que se indique otra cosa, los criterios usan un desafío con tres bonificaciones:
B1 `OBJECTIVES ≥ 5` → 15, B2 `TIME ≤ 60` → 10 y B3 `ENERGY ≤ 40` → 10, y un tope de 25.

**AC-1: La suma supera el tope.**
Given una corrida que cumple B1, B2 y B3,
When se puntúa,
Then las bonificaciones obtenidas son 35, las aplicadas 25 y el recorte 10;
and `totalOf(BONUS)` es 25.

**AC-2: Bonificaciones menores que el tope.**
Given el escenario de AC-1, donde cada bonificación (15, 10, 10) es menor que el tope,
When se puntúa,
Then igual hay recorte, porque el tope se compara contra la suma y no contra cada una.

**AC-3: Una sola bonificación mayor que el tope.**
Given un desafío con una única bonificación de 40 y un tope de 25,
When una corrida la obtiene,
Then las aplicadas son 25 y el recorte 15;
and el desafío se construye sin error, aunque la bonificación supere el tope.

**AC-4: Dentro del tope y en el límite.**
Given una corrida que cumple sólo B1 y B2 (obtenidas = 25 = tope),
When se puntúa,
Then el recorte es 0 y el total es igual al que tendría sin tope.

**AC-5: Tope cero.**
Given un desafío con tope 0 y una corrida que cumple B1,
When se puntúa,
Then las aplicadas son 0 y el recorte 15.

**AC-6: Ganados y penalizaciones no cuentan.**
Given la corrida del ejemplo de FR-6 (ganados 50, bonificaciones 35, penalización -3),
When se puntúa con tope 25,
Then el total es 72;
and `totalOf(EARNED)` es 50 y `totalOf(PENALTY)` es -3, los mismos que sin tope.

**AC-7: Explicación con recorte.**
Given la corrida de AC-1,
When se consulta su puntaje con `CalculateRunScore`,
Then el desglose contiene las tres bonificaciones con sus valores originales (15, 10, 10);
and contiene una contribución `BONUS_CAP` de -10 cuya explicación indica 35.00 obtenidas,
tope 25.00 y recorte 10.00.

**AC-8: Explicación sin recorte.**
Given la corrida de AC-4,
When se puntúa,
Then el desglose contiene una contribución `BONUS_CAP` de 0 cuya explicación indica 25.00
obtenidas, tope 25.00 y que no hubo recorte.

**AC-9: Ninguna bonificación obtenida.**
Given una corrida que no cumple ningún umbral,
When se puntúa,
Then la contribución `BONUS_CAP` indica 0 obtenidas y no hay recorte.

**AC-10: Regla de bonificación nueva.**
Given un desafío con tope 25 que combina `ThresholdBonusRule` (15) con una `ScoringRule` definida
sólo en el test que emite una contribución `BONUS` de 20,
When se puntúa,
Then las obtenidas son 35, las aplicadas 25 y el recorte 10,
sin modificar el tope ni las reglas.

**AC-11: Reglas sin cambios.**
Given una `ThresholdBonusRule` de un desafío con tope,
When se aplica directamente sobre un `ScoringContext`,
Then devuelve las mismas contribuciones que sin tope;
and `ScoringRulesTest` pasa sin cambios.

**AC-12: Configuración inválida.**
Given un tope de bonificaciones negativo,
When se intenta configurar el desafío,
Then se lanza `InvalidValueException`.

**AC-13: Sin tope, todo igual.**
Given el demo y los fixtures existentes, ninguno con tope configurado,
When se ejecuta la suite de tests existente,
Then todos los tests pasan sin modificar sus expectativas;
and ningún desglose contiene la contribución `BONUS_CAP`.

**AC-14: Desempates.**
Given dos equipos empatados en total, uno con un recorte de 10 y el otro sin recorte, con las mismas
penalizaciones,
When se aplica `FewestPenaltiesTiebreak`,
Then ambos tienen los mismos puntos de penalización: el recorte no cuenta como penalización.

**AC-15: Versiones con y sin tope.**
Given una corrida capturada en una ronda con un reglamento `v1` sin tope, y luego publicada una
versión `v2` con tope 25,
When se vuelve a puntuar la corrida de `v1`,
Then no tiene recorte;
and una corrida que obtiene las mismas bonificaciones en una ronda programada con `v2` sí lo tiene.

**AC-16: Corrección que cambia las bonificaciones.**
Given la corrida de AC-1, recortada en 10,
When una corrección baja `OBJECTIVES` a 4 (B1 deja de cumplirse) y se recalcula la tabla,
Then las obtenidas son 20 y no hay recorte en la nueva revisión;
and la revisión anterior conserva el total recortado.

## 5. Out of scope

- Topes por grupos de bonificaciones o por tipo de bonificación.
- Elegir qué bonificación individual absorbe el recorte, o repartirlo entre ellas.
- Topes o pisos sobre puntos ganados (`EARNED`), penalizaciones o el total de la corrida.
- Cambiar los topes por regla que ya existen (`TimeScoringRule`/`PrecisionScoringRule.maximumPoints`).
- Validar que el tope sea alcanzable o que el desafío tenga reglas que bonifiquen: el desafío no
  puede saberlo sin agregar métodos a `ScoringRule` (C-2).
- API REST, persistencia real o formato de presentación de la explicación.

## 6. Open questions

**OQ-1: ¿El tope se aplica a cada corrida o al acumulado del equipo en el desafío?** Esta spec
propone **por corrida**. "La suma de todas las bonificaciones de un desafío" también puede leerse
como el acumulado de un equipo entre intentos y rondas, y la diferencia importa con
`SumOfAttempts` y con la configuración de mejores N de M. Por ejemplo, con dos intentos que obtienen
25 de bonificación cada uno, por corrida suman 50 y con un tope acumulado suman 25. Si el tope es
acumulado, deja de vivir en `ChallengeSpec.score` y pasa al ranking (`TeamRuns`/`AttemptAggregation`).
`CalculateRunScore` ya no mostraría el recorte, y la explicación tendría que ir en la tabla de
posiciones, como la que introduce `SPEC-mejores-n-de-m-rondas.md`. Afecta FR-4, FR-5, FR-8 y casi
todos los criterios de aceptación.

**OQ-2: ¿El tope se configura por desafío o como un único valor del reglamento?** Esta spec propone
**por desafío**, con un valor propio en cada `ChallengeSpec`, porque cada desafío tiene bonificaciones
de distinta escala, igual que N y M en la spec de rondas. "El reglamento incorpora un tope" también
admite un único valor en `RulebookDraft` que se aplique a cada desafío por separado. Esa opción
cambia dónde vive la configuración y qué constructor se extiende (C-4). Una combinación de ambas,
con un valor por defecto en el reglamento que cada desafío pueda redefinir, queda fuera de alcance
salvo que se decida lo contrario.
