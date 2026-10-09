# Correcciones — Entrega 1

> Cada corrección lleva un **Estado** para tener trazabilidad de los cambios a realizar. Valores posibles: `Pendiente` · `En curso` · `Resuelta` · `Descartada`.

## Índice

1. [Modelado del dominio](#1-modelado-del-dominio)
   - [Casos de uso](#1-2-casos-de-uso)
2. [Separación negocio-detalles](#2-separación-negocio-detalles)
3. [SOLID](#3-solid)
4. [Patrones](#4-patrones)
5. [Arquitectura](#5-arquitectura)

---

## 1. Modelado del dominio

### ✅ Bien

- **El puntaje es un desglose explicable, no un número.** Cada `ScoringRule` devuelve `ScoreContribution(ruleCode, kind, explanation, points)`, incluso cuando aporta 0. `ContributionKind` permite que el ranking reconozca las penalizaciones sin conocer las reglas concretas.
- **Recálculo determinista.** La versión del reglamento queda fijada en la ronda y copiada en el `RunResult`, así que el recálculo es determinista aunque después se publique otra versión.
- **Ids tipados.** Hay 11 ids tipados (`TeamId`, `RunId`…) que no se pueden intercambiar al compilar.
- **Inmutabilidad.** `Standings` son revisiones inmutables (`PROVISIONAL`/`FINAL`), y `RunResult` conserva los valores originales más las correcciones.

### ❌ Falta

#### Regla de negocio mal modelada: agregación de intentos

**Estado: Resuelta** — la agregación es ahora una política versionada del reglamento: `AttemptAggregation` (`BestAttempt`, `SumOfAttempts`) en `Rulebook.attemptAggregation`, aplicada por `TeamScoreSummary.totalPoints()`. Ver `DESIGN.md` 3.5, `AttemptAggregationTest`, `RankingServiceTest` y `StandingsLifecycleTest`.

El ranking suma todos los intentos, así que **dos intentos de 30 le ganan a uno de 50**. La agregación tiene que ser una política del reglamento (`BestAttempt`, `SumOfAttempts`…).

El `DESIGN.md` lo documenta como decisión (4.8: *"no selecciona sólo el mejor intento"*) y lo deja como evolución futura (sección 8), pero **documentarlo no lo vuelve correcto**:

```java
public Points totalPoints() {   // TeamScoreSummary
    return runs.stream().map(ScoredRun::total).reduce(Points.ZERO, Points::plus);
}
```

#### Los VOs de configuración no protegen sus invariantes

**Estado: Resuelta** — ver `DESIGN.md` 2.1, 2.1.1, 2.2 y 3.1.

- `ResourceScoringRule` solo valida que no haya nulos, así que con `pointsPerUnitOver` negativo una "penalización" suma +50. Lo mismo pasa con pesos, topes y bonos negativos. — **Estado: Resuelta** (coeficientes como `PointsRate`, topes/bonos/deducciones como `PointsAmount` y umbrales como `MetricValue`, ninguno admite negativos; `TimeScoringRule` exige referencia positiva. `ScoringRulesTest`)
- `ChallengeSpec` acepta:
  - **Penalizaciones duplicadas**: revienta con `IllegalStateException` recién al puntuar, en `PenaltyScoringRule.of`. — **Estado: Resuelta** (el constructor de `ChallengeSpec` lanza `InvalidValueException`; `PenaltyScoringRule.of` también. `ChallengeSpecTest`, `ScoringRulesTest`)
  - **Métricas duplicadas sin ningún error**: `definitionOf` usa `findFirst`, y una misma clave declarada con dos `MetricKind` distintos se valida dos veces. — **Estado: Resuelta** (el constructor rechaza claves repetidas aunque cambie el `MetricKind`. `ChallengeSpecTest`)
  - **Reglas sobre métricas que no existen**: aportan 0 sin avisar. — **Estado: Resuelta** (`ScoringRule.referencedMetrics()` y el constructor de `ChallengeSpec` exige que estén definidas. `ChallengeSpecTest`)
- `Rulebook.of` pisa en silencio los desafíos con id repetido. — **Estado: Resuelta** (lanza `InvalidValueException`. `DomainEdgeCasesTest`)

#### El agregado deja saltear sus reglas

**Estado: Resuelta** — ver `DESIGN.md` 3.3, 4.1 y 4.3.

- `TeamRegistration.accept()` no exige un veredicto, así que un equipo inelegible queda `ACCEPTED`. — **Estado: Resuelta** (`accept()`/`reject()` reemplazados por `resolveWith(EligibilityVerdict)`. `TeamRegistrationTest`)
- `RunResult.applyCorrection` no valida las mediciones contra el desafío. — **Estado: Resuelta** (`applyCorrection(correction, challenge)` rechaza otro desafío y valida mediciones e incidentes. `RunResultTest`)
- **Mutadores públicos que evitan el caso de uso:**
  - `Competition.addCategory` es público y solo lo usa el constructor, así que se pueden agregar categorías a una competencia sin pasar por ningún caso de uso ni auditoría. — **Estado: Resuelta** (ahora es privado)
  - El constructor de `Standings` también es público: se puede armar una revisión `FINAL` sin pasar por `publish()`. — **Estado: Resuelta** (`Standings` pasó de `record` a clase con constructor privado; solo se obtiene con `provisional`, `publish` y `supersede`)
  - Relacionado: los agregados `Competition`, `TeamRegistration`, `Round`, `RunResult` y `Appeal` ahora son **inmutables**; sus transiciones devuelven una instancia nueva y el constructor público sólo crea el estado inicial. `Round.schedule` exige la inscripción del equipo y `RunResult.capture` valida la captura. Ver `DESIGN.md` 4.11.

#### Evaluaciones de jueces sin restricciones

**Estado: Resuelta** — `JudgeEvaluations` rechaza que un juez evalúe dos veces el mismo criterio y `ChallengeSpec.validateEvaluations(evaluations, heat.judges())` verifica que el criterio sea una métrica `JUDGE_CRITERION` del desafío y que el juez esté en el heat; la invoca `RunResult.capture`, la fábrica de una corrida (antes `CaptureRunResultUseCase`). Ver `DESIGN.md` 4.10, `JudgeEvaluationsTest`, `ChallengeSpecTest` y `CaptureRunResultUseCaseTest`.

No se modelan las restricciones de las evaluaciones de jueces: el mismo juez puede evaluar dos veces (pesa doble) y no se verifica que esté en `heat.judges()`.

#### Colecciones sueltas en lugar de conceptos propios

**Estado: Resuelta** — `JudgeEvaluations`, `TeamRuns` (donde se aplica la `AttemptAggregation`), `CorrectionHistory` (orden cronológico), `Heats` (un turno por equipo) y `TeamMembers` (sin integrantes repetidos). Ver `DESIGN.md` 4.10.

`List<JudgeEvaluation>`, `List<ScoredRun>`, `List<ResultCorrection>`, `List<Heat>`, `List<Member>`.

- Un `JudgeEvaluations` es el lugar natural para la invariante del punto anterior (*"un juez evalúa una vez cada criterio y pertenece al heat"*).
- Un `TeamRuns` sería donde vive la política de agregación de intentos.

Además de aportar lenguaje ubicuo, permiten poner restricciones sobre esas colecciones.

#### Primitive obsession que contradice su propio criterio

**Estado: Resuelta** — ver la tabla de `DESIGN.md` 4.6.

- `AppealDecision.reviewer`, `AuditEvent.actor/subject`, `ResultCorrection.actor` y `AuditLog.findBySubject(String)` usan `String`. — **Estado: Resuelta** (`Actor` para actor y reviewer, también en todos los `Command`; el sujeto es un `Identifier` y `findBySubject(Identifier)`)
- `EligibilityViolation.ruleCode` es `String`, cuando para puntaje sí crearon `ScoringRuleCode`. — **Estado: Resuelta** (`EligibilityRuleCode`)
- `TeamRegistration.rejectionReasons` es `List<String>`: `verdict.reasons()` convierte las violaciones tipadas en texto y el agregado pierde qué regla falló. — **Estado: Resuelta** (`List<EligibilityViolation>`; `reasons()` solo se usa para el texto de auditoría)
- `AuditEvent.details` es un `Map<String,String>`, y los casos de uso lo llenan con `measurements().values().toString()`. — **Estado: Resuelta** (`Map<AuditDetail, String>` con claves de un enum; `MeasurementSet.toString()` describe las mediciones ordenadas por clave)
- `int` sueltos: `attemptNumber`, `ordinal`, `revision`, `maximumAttempts`. — **Estado: Resuelta** (`AttemptNumber`, `RoundOrdinal`, `Revision`, `AttemptLimit`)
- `Robot.weightKg` como `BigDecimal`. — **Estado: Resuelta** (`Weight`, también en `RobotSpecificationRule`)
- `MetricDefinition.unit` como `String` con un flag `boolean required`. — **Estado: Resuelta** (`MetricUnit` y `MetricRequirement`)

#### `Member` sin identidad

**Estado: Resuelta** — `Member` lleva un `MemberId`; `RegisterTeam.Command` recibe `MemberDraft` y el caso de uso asigna los ids con `IdGenerator.nextMemberId()`, como `CreateCompetition` hace con `CategoryDraft`. Ver `DESIGN.md` 4.6 y `DomainEdgeCasesTest.membersHaveTheirOwnIdentityAndCannotBeListedTwice`.

`Member(String fullName, LocalDate birthDate, MemberRole role)` no tiene id: dos integrantes homónimos nacidos el mismo día son el mismo integrante.

#### `Points` representa conceptos distintos

**Estado: Resuelta** — `Points` queda para contribuciones y totales; la nota de un juez es `JudgeScore` (escala 0-10), los coeficientes son `PointsRate` y los montos fijos configurados (topes, bono, deducción) son `PointsAmount`, ambos no negativos. Ver `DESIGN.md` 2.1 y 4.6.

`Points` se usa para:

- el puntaje de una contribución,
- la nota de un juez (`JudgeEvaluation.score`),
- el bono configurado (`ThresholdBonusRule.bonus`),
- el coeficiente `pointsPerUnitOver`.

Comparten la aritmética, pero no las reglas: una nota de juez tiene una escala (por ejemplo, 0-10) y un coeficiente de configuración no debería poder ser negativo. Se está representando con un mismo modelo lo que en el dominio son conceptos diferentes.

#### Una única excepción de dominio

**Estado: Resuelta** — `DomainException` es abstracta y tiene cuatro categorías: `InvalidValueException`, `RuleViolationException`, `ConflictException` y `NotFoundException` (movida de `application` a `domain.shared`); `ScheduleConflictException` expone los conflictos detectados. Ver `DESIGN.md` 4.9.

Hay una única `DomainException` para cientos de reglas, así que no se puede distinguir qué regla falló sin parsear el mensaje. Además, `NotFoundException` extiende `RuntimeException` y vive en `application`, fuera de cualquier jerarquía.

---

### 1-2. Casos de uso

#### CreateSeason

- Las invariantes viven en el record `Season`. No se verifica que la temporada no exista: si la unicidad importa, es una regla que depende del repositorio y va en el caso de uso, con la misma salvedad que en [CaptureRunResult](#capturerunresult). — **Estado: Resuelta** (la regla elegida es que dos temporadas no se superpongan, lo que también impide repetir una. El caso de uso sólo carga las temporadas (`SeasonRepository.findAll`) y decide `SeasonCalendar.requireAvailable(period)` en el dominio, con `ConflictException`. La salvedad de concurrencia está documentada. Ver `DESIGN.md` 4.10 y 4.12, `EventConfigurationUseCaseTest` y `DomainEdgeCasesTest`)

#### CreateCompetition

- ✅ La regla que cruza agregados la resuelve `Season` con un método que dice lo que exige (`requireCompetitionPeriodInside`).
- La competencia nace sin reglamento (`activeRulebookVersion` en `null`) hasta que se ejecuta `PublishRulebook`. — **Estado: Resuelta** (`CreateCompetition.Command` recibe un `RulebookDraft`; la competencia nace con `v1` activa y el reglamento guardado. `activeRulebookVersion()` ya no es `Optional` y desapareció `requireActiveRulebookVersion()`. Además `Competition` es inmutable: `activateRulebook` devuelve una competencia nueva. Ver `DESIGN.md` 3.1 y 4.11, `EventConfigurationUseCaseTest`)

> Idealmente los modelos del dominio deberían ser completos al momento de su creación e inmutables, creando nuevos y destruyéndolos cada vez que se requiere una modificación.

#### PublishRulebook

- ✅ La versión se calcula a partir de la última publicada y lo ya programado conserva su versión.
- ❌ `Rulebook.of` no detecta ids de desafío repetidos: el desafío anterior se pierde sin error. — **Estado: Resuelta** (ver [VOs de configuración](#los-vos-de-configuración-no-protegen-sus-invariantes))

#### RegisterTeam

La elegibilidad es un Strategy del reglamento (`EligibilityPolicy`), pero **la decisión se toma en el caso de uso**: — **Estado: Resuelta** (`registration.resolveWith(verdict)`)

```java
if (verdict.isEligible()) {
    registration.accept();          // el agregado no ve el veredicto
} else {
    registration.reject(verdict.reasons());
}
```

Con `registration.resolveWith(verdict)` el `if` desaparece del caso de uso y el agregado no puede quedar `ACCEPTED` sin un veredicto elegible.

Otras observaciones:

- Es raro que `EligibilityPolicy` a su vez implemente `EligibilityRule`, y que además contenga una lista de rules que también son `EligibilityRule`s. — **Estado: Resuelta** (ya no implementa `EligibilityRule`: sólo contiene las reglas y ofrece `verdictFor`. Ver `DESIGN.md` 4.1 y 5.10)
- El nombre de la clase es un poco desafortunado, ya que la palabra *policy* tiene un fuerte significado en el desarrollo de software, ligado al patrón Policy, que correctamente intentan implementar. Se podría cambiar el nombre de la interfaz o hacer que la clase policy implemente otra interfaz diferente. — **Estado: Resuelta** (se renombró a `EligibilityRequirements`, los requisitos de elegibilidad del reglamento; la estrategia intercambiable es `EligibilityRule`. `EligibilityRequirementsTest`)

#### ScheduleRound

- ✅ `ScheduleConflictDetector` es un servicio de dominio, y el guardado queda al final, así que si falla el tercer heat no queda una ronda a medio programar.
- ❌ "Equipo aceptado y de la categoría" (`requireEligibleTeam`) es una regla de negocio que vive en el caso de uso. — **Estado: Resuelta** (`TeamRegistration.requireAcceptedIn(competitionId, categoryId)`, invocada por `Round.schedule(heat, team)`: no se puede agregar un turno sin presentar la inscripción. También pasaron al dominio el período del turno (`Competition.requireSlotWithinPeriod`) y el rechazo por conflictos (`ScheduleConflictDetector.requireNoConflicts`). Ver `DESIGN.md` 4.1 y 4.2, `DomainEdgeCasesTest`, `ScheduleRoundUseCaseTest`)
- ❌ No se valida que el ordinal de la ronda sea único en la categoría. — **Estado: Resuelta** (`CompetitionSchedule.requireAvailableOrdinal(categoryId, ordinal)` con `ConflictException`. Ver `DESIGN.md` 4.2, `ScheduleRoundUseCaseTest.rejectsARoundOrdinalThatIsAlreadyScheduledInTheCategory`)

#### CaptureRunResult

- ✅ Valida mediciones e incidentes contra el `ChallengeSpec` de la versión con la que se programó la ronda.
- ❌ **No valida las evaluaciones de los jueces**: el mismo juez puede evaluar dos veces el mismo criterio (`J1=10, J1=10, J2=0 → 6.67` en lugar de `5`), y no se verifica que el juez esté en el heat ni que el criterio exista. Falta un `challenge.validateEvaluations(evaluations, heat.judges())`. — **Estado: Resuelta** (ver [evaluaciones de jueces](#evaluaciones-de-jueces-sin-restricciones))
- ⚠️ "El intento no se capturó antes" (`requireUnusedAttempt`) se fuerza en el caso de uso consultando el repositorio. Es positivo para no quedar acoplado a un vendor de persistencia, pero no escala a escenarios multithread: se podría agregar un constraint del lado de persistencia. — **Estado: Resuelta** (la regla la decide el dominio, `RoundResults.requireUnusedAttempt`, y el contrato de `RunResultRepository.save` suma el constraint: rechaza otra corrida para la misma ronda, equipo e intento. `InMemoryRunResultRepository` lo implementa con métodos `synchronized`. Ver `DESIGN.md` 4.3 y 4.12, `RunResultRepositoryContractTest`, `RunResultTest`)
- ❌ No se verifica que las posiciones de la categoría no estén ya publicadas como `FINAL`. — **Estado: Resuelta** (`StandingsHistory.requireOpenForResults()`: si alguna revisión fue `FINAL`, la captura se rechaza aunque un recálculo posterior haya dejado la última como provisional. Ver `DESIGN.md` 3.3, `StandingsHistoryTest`, `StandingsLifecycleTest`)
- Relacionado: las validaciones de la captura (turno del equipo, desafío de la ronda, intentos, mediciones, incidentes, evaluaciones) se movieron a la fábrica `RunResult.capture`, y `Round.heatFor` lanza si el equipo no tiene turno. Ver `DESIGN.md` 4.3.

#### CalculateRunScore

Consulta pura: el puntaje se recalcula cada vez desde las mediciones vigentes.

- ❌ `CategoryScoringService` es lógica de negocio (cómo se puntúa una corrida) y vive en `application/service`: debería ser un servicio del dominio. — **Estado: Resuelta** (ahora es `domain.ranking.CategoryScoringService` y depende de interfaces de repositorio del dominio; suma `rank` y `runsOf`. Ver `DESIGN.md` 4.8)

#### SubmitAppeal

Valida que el equipo apele una corrida propia.

- ❌ No hay plazo de apelación. — **Estado: Resuelta** (`AppealWindow` en el reglamento; `Appeal.file` exige presentar a más tardar `length` después de la captura, con el plazo de la versión fijada en la corrida. Ver `DESIGN.md` 4.5, `AppealTest`, `AppealRecalculationTest.anAppealIsAcceptedOnlyWithinTheWindowOfTheRulebookPinnedInTheRun`)
- ❌ No hay control de apelaciones duplicadas sobre la misma corrida. — **Estado: Resuelta** (`Appeals.requireNoneOn(runId)` sobre `AppealRepository.findByRun`: una corrida se apela una sola vez, aunque la primera apelación ya esté resuelta. `AppealRecalculationTest.aRunCanBeAppealedOnlyOnceEvenAfterTheFirstAppealWasResolved`)
- Relacionado: "el equipo apela una corrida propia" también pasó del caso de uso a `Appeal.file`.

#### ResolveAppeal

✅ La corrección se valida antes de resolver, y no pisa la medición original sino que agrega un `ResultCorrection`. Sin embargo:

- **Flag booleano en el comando**: dos casos de uso (`AcceptAppeal`/`RejectAppeal`) serían más claros. Si se acepta sin corrección, queda `ACCEPTED` sin efecto sobre el resultado, y si se rechaza con corrección, la corrección se descarta sin avisar. — **Estado: Resuelta** (`ResolveAppeal` se reemplazó por `AcceptAppeal`, con corrección obligatoria, y `RejectAppeal`, sin corrección. Ver `DESIGN.md` 4.5)
- **Demasiadas responsabilidades**: resuelve, valida, aplica la corrección y audita dos eventos. — **Estado: Resuelta** (resolver es `Appeal.accept`/`reject`; validar y aplicar la corrección es `RunResult.applyCorrection`; recalcular es el puerto `RecalculateStandings`. `AcceptAppealUseCase` sólo carga, invoca al dominio, persiste y audita, y `RejectAppealUseCase` es independiente)
- **No dispara el recálculo de posiciones**: quien llama tiene que acordarse de ejecutar `RecalculateStandings`. — **Estado: Resuelta** (si la categoría ya tiene posiciones, `AcceptAppealUseCase` invoca `RecalculateStandings`. `AppealRecalculationTest.acceptingAnAppealRecalculatesTheStandingsWithTheSameRulebookVersion`; ver `DESIGN.md` 4.5 y 5.3)
- **Orden de persistencia incorrecto**: guarda la apelación (`appeals.save`) antes de aplicar la corrección. Si `apply` falla, la apelación queda `ACCEPTED` y la corrida sin corregir. El `DESIGN.md` lo reconoce (sección 8) pero no lo resuelve. Mutar todo en memoria y persistir al final lo evita. — **Estado: Resuelta** (`Appeal` y `RunResult` son inmutables: el caso de uso obtiene la apelación aceptada y la corrida corregida en memoria y recién después guarda. `AppealRecalculationTest.anInvalidCorrectionLeavesTheAppealTheRunAndTheStandingsAsTheyWere`; ver `DESIGN.md` 4.5 y 4.11)

#### GenerateStandings

- ❌ Es donde se ve la [regla mal modelada](#regla-de-negocio-mal-modelada-agregación-de-intentos): entre `collect` (todos los intentos de cada equipo, sin filtrar) y `totalPoints()` no hay ningún paso que elija "el mejor intento". — **Estado: Resuelta** (`GenerateStandingsUseCase` pasa `rulebook.attemptAggregation()` a `collect`)
- ❌ "No generar dos veces la misma tabla" es otra regla de negocio que se decide en el caso de uso consultando el repositorio (`standings.findLatest(...).isPresent()`). — **Estado: Resuelta** (`StandingsHistory.generate` crea la revisión 1 o lanza `ConflictException`. Ver `DESIGN.md` 3.3, `StandingsHistoryTest`)

#### RecalculateStandings

- ✅ `Standings` es inmutable y `supersede` devuelve la revisión n+1 con el historial completo.
- ⚠️ Recalcular reabre también posiciones `FINAL`, lo cual es razonable (una apelación tardía), pero tendría que ser una decisión explícita en el `DESIGN.md`. — **Estado: Resuelta** (documentada en `DESIGN.md` 3.3: se agrega una revisión `n+1` provisional, la `FINAL` queda en el historial y hay que volver a publicar. La captura sigue cerrada y el plazo de apelación acota cuándo puede pasar)

#### PublishStandings

- ✅ Rechaza publicar dos veces.
- ❌ No verifica que no haya apelaciones pendientes sobre corridas de la categoría. — **Estado: Resuelta** (`Appeals.requireNonePending()` sobre las apelaciones de las corridas de la categoría. `AppealRecalculationTest.standingsCannotBePublishedWhileAnAppealOfTheCategoryIsPending`; ver `DESIGN.md` 3.3)

#### Consultas (`Find*`)

- ✅ `FindCompetition` proyecta el agregado a una vista.
- ❌ `FindRound`, `FindRunResult`, `FindTeamRegistration` y `FindAppeal` devuelven el agregado mutable. Como `accept()` y `applyCorrection()` son públicos, quien consulta puede aceptar una inscripción o corregir un resultado salteando el caso de uso y la auditoría. — **Estado: Resuelta** (los agregados son inmutables: las transiciones devuelven una instancia nueva que sólo un caso de uso persiste, así que una consulta no abre un camino alternativo. Ver `DESIGN.md` 1.2 y 4.11)
- ⚠️ Son 7 puertos sin otro consumidor que tests y demo, cuando su propio `DESIGN.md` dice *"no se crean interfaces sin cliente"*. Esto es esperable para esta entrega; en el futuro considerar si realmente existe un usuario que requiera estos casos de uso, o si son únicamente requisitos internos de la aplicación. Si lo último es cierto, es una buena señal para que no sean casos de uso. — **Estado: Descartada** (por ahora se conservan: cada una responde a una necesidad de un usuario de la Entrega 2, como un equipo que consulta su inscripción, su corrida o su apelación. `DESIGN.md` 1.2 deja asentado que se eliminan las que no tengan consumidor externo al diseñar la API)

---

## 2. Separación negocio-detalles

### ✅ Bien

`domain` no importa nada de `application` ni de `infrastructure`, `Clock` e `IdGenerator` están inyectados y los adaptadores en memoria están afuera.

### ❌ Falta

**Hay reglas de negocio en los casos de uso.** Los casos de uso tienen que coordinar, no decidir:

| Regla | Dónde vive hoy | Estado |
|---|---|---|
| "El intento no se capturó antes" | `CaptureRunResultUseCase.requireUnusedAttempt` | Resuelta (`RoundResults.requireUnusedAttempt` + constraint en `RunResultRepository`) |
| "Equipo aceptado y de la categoría" | `ScheduleRoundUseCase.requireEligibleTeam` | Resuelta (`TeamRegistration.requireAcceptedIn`, invocada por `Round.schedule`) |
| "Veredicto → accept/reject" | `RegisterTeamUseCase` | Resuelta (`TeamRegistration.resolveWith`) |
| "No generar dos veces la misma tabla" | `GenerateStandingsUseCase` | Resuelta (`StandingsHistory.generate`) |

Además se movieron al dominio otras decisiones que quedaban en los casos de uso: equipo con turno y validación de la captura (`Round.heatFor`, `RunResult.capture`), turno dentro del período (`Competition.requireSlotWithinPeriod`), rechazo por conflictos de agenda (`ScheduleConflictDetector.requireNoConflicts`) y apelar una corrida propia (`Appeal.file`). Las reglas nuevas también viven en el dominio: ordinal único, temporadas sin superposición, plazo y duplicados de apelación, apelaciones pendientes al publicar y captura cerrada tras posiciones `FINAL`. El criterio general está en `DESIGN.md` 4.12.

**`CategoryScoringService` está en `application/service` pero es un servicio de dominio.** El `DESIGN.md` (4.8) lo justifica diciendo que en el dominio *"lo obligaría a conocer repositorios"*, pero esa justificación supone que los repositorios no son del dominio (ver [punto 5](#5-arquitectura)). Si las interfaces de repositorio son del dominio, el argumento deja de valer. Otra opción es que el servicio reciba las corridas y los reglamentos ya cargados y sea puro. — **Estado: Resuelta** (`domain.ranking.CategoryScoringService`, que depende de las interfaces de repositorio del dominio. Ver `DESIGN.md` 1.3 y 4.8)

---

## 3. SOLID

| Principio | Estado | Comentario | Estado de la corrección |
|---|---|---|---|
| **OCP** | ✅ | Una fórmula, un desempate o una restricción nueva es una clase nueva. | — |
| **OCP** | ⚠️ | `MetricKind.accepts` hace `switch (this)` con `default`. Lo polimórfico sería un método abstracto por constante. Lo mismo pasa con `ThresholdBonusRule.Comparison` (`switch (comparison)` sobre `AT_LEAST`/`AT_MOST`). | Resuelta (`accepts` e `isMetBy` son abstractos y cada constante los implementa; `DESIGN.md` 4.6, `DomainEdgeCasesTest.everyMetricKindDecidesPolymorphicallyAndRejectsNegativeAmounts`) |
| **LSP** | ✅ / ⚠️ | El contrato común de las 7 `ScoringRule` (dato ausente → 0 explicado) se verifica con un test parametrizado. Pero el contrato no alcanza a la configuración: una regla con parámetros negativos cumple la firma y rompe la semántica de `ContributionKind`. | Resuelta (`PointsRate`/`PointsAmount`, `DESIGN.md` 2.1.1) |
| **SRP** | ❌ | `ResolveAppealUseCase` concentra responsabilidades que responden a distintos actores, cada uno con necesidades de cambio independientes. | Resuelta (`AcceptAppeal` y `RejectAppeal`; las reglas en `Appeal` y `RunResult`, el recálculo en su propio puerto; `DESIGN.md` 4.5) |
| **ISP** | ✅ / ⚠️ | Los puertos son chicos, con una operación cada uno. Pero `ScoringRule` tiene un método `default breakdownFor` que no usa ningún código de producción, solo los tests (ver nota abajo). | Resuelta (se eliminó `breakdownFor`; también `RulebookRepository.findLatest`, que quedó sin cliente; `DESIGN.md` 2.1.1 y 5.9) |
| **DIP** | ✅ | `Clock`, `IdGenerator`, repositorios y `AuditLog` se inyectan por constructor desde la raíz de composición. | — |

> **Sobre `breakdownFor`:** contradice la sección 5.9 de su propio `DESIGN.md` (*"métodos por si acaso"*). Los métodos `default` son trampas donde es fácil caer; no son necesariamente malos, pero en el afán de ahorrar código podemos terminar implementando métodos donde no aplican, solamente para comunicar este hecho.

---

## 4. Patrones

### ✅ Bien elegidos

- **Strategy**: `ScoringRule`.
- **Specification/Composite**: `EligibilityPolicy` acumula todas las violaciones.
- **`TiebreakRule extends Comparator`**: reutiliza `thenComparing`.

### ❌ A corregir

- **Flag booleano** en `ResolveAppeal.Command`. — **Estado: Resuelta** (dos casos de uso, `AcceptAppeal` y `RejectAppeal`; ver [ResolveAppeal](#resolveappeal))
- **`Optional` como campo** va contra lo visto en clase: se usa solo como tipo de retorno. Aparece en: — **Estado: Resuelta** (ver `DESIGN.md` 4.6)
  - `ResolveAppeal.Command.correction` — desapareció junto con el comando; `AcceptAppeal.Command` exige la corrección
  - `ResultCorrection.sourceAppeal` (el `DESIGN.md` 4.3 lo presenta como decisión) — ahora es `AppealId` obligatorio: toda corrección nace de una apelación
  - `FindCompetition.View.activeRulebookVersion` — ahora es `RulebookVersion`: la competencia nace con su reglamento

```java
record Command(AppealId appealId, boolean accepted, String reviewer, String rationale,
               Optional<Correction> correction, String actor) {}
// ...
if (command.accepted()) { appeal.accept(decision); } else { appeal.reject(decision); }
```

---

## 5. Arquitectura

### ✅ Bien

**Clean/Hexagonal bien encarado**: 19 puertos driving (`port.in`), puertos driven (`port.out`), interactors y una raíz de composición. Los casos de uso reciben `Command` con ids, no entidades.

### ❌ A revisar

- **Ubicación de los puertos.** Los puertos driven (`*Repository`, `AuditLog`) están en `application.port.out`. Como se vio en clase, la interfaz del gateway/repositorio pertenece al negocio y solo la implementación es un detalle, así que **el dominio debería ser dueño de sus repositorios**. Lo mismo aplica a los puertos driving: las interfaces de los casos de uso (`port.in`) también son parte del dominio, y los interactors las implementan. El `DESIGN.md` (1.1 y 1.3) justifica lo contrario (*"repositorios declarados por la aplicación"*), y esa justificación es la que habría que revisar. — **Estado: Resuelta** (las interfaces de casos de uso están en `domain.port.in` y los repositorios, `AuditLog` e `IdGenerator`, en el paquete de cada agregado del dominio; `application` sólo tiene los interactors. Ver `DESIGN.md` 1.1, 1.2 y 1.3)
- **Primitive obsession.** La sección 4.6 dice que evitan primitive obsession, lo cual no se cumple en los casos listados en el [punto 1](#primitive-obsession-que-contradice-su-propio-criterio). — **Estado: Resuelta**
- **DDD.** Los agregados están bien delimitados y referenciados por id, pero son **anémicos en los bordes**, porque las reglas se aplican desde la capa de aplicación (ver [punto 2](#2-separación-negocio-detalles)). — **Estado: Resuelta** (las reglas de borde pasaron a los agregados y a colecciones del dominio, y los agregados son inmutables. Ver `DESIGN.md` 4.11 y 4.12)
- **Jerarquía de excepciones.** Una jerarquía (`NotFound`, `RuleViolation`…) va a hacer falta para mapear 404/409/422 en la Entrega 2. — **Estado: Resuelta** (ver [una única excepción de dominio](#una-única-excepción-de-dominio))
