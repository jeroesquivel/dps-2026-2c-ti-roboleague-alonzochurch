# Correcciones — Entrega 2

> Cada corrección lleva un **Estado** para tener trazabilidad de los cambios a realizar. Valores posibles: `Pendiente` · `En curso` · `Resuelta` · `Descartada`.

## Índice

1. [Correcciones de la Entrega 1](#1-correcciones-de-la-entrega-1)
2. [Funcionalidades nuevas](#2-funcionalidades-nuevas)
3. [Persistencia](#3-persistencia)
4. [Observaciones](#4-observaciones)
5. [Residuos detectados al revisar las correcciones](#5-residuos-detectados-al-revisar-las-correcciones)

---

## 1. Correcciones de la Entrega 1

#### `Optional` como campo en `ChallengeSpec`

> Sigue presente el uso de `Optional` como campo en `ChallengeSpec`.

**Estado: Resuelta** — `ChallengeSpec` ya no guarda `Optional`: el tope de bonificaciones es una
`BonusPolicy` sellada (`NoBonusCap` / `BonusCap`) y el modo de captura un `CaptureMode`
(`SingleCapture` / `MixedSources`). `Optional` queda sólo como tipo de retorno
(`bonusCapOutcome`, `scoreBySource`). Ver `DESIGN.md` 4.6, `NullObjectVariantsTest` y
`ChallengeSpecBonusCapTest`. El mismo problema seguía en `CalculateRunScore.RunScore`; ver
[sección 5](#optional-como-campo-en-calculaterunscorerunscore).

---

## 2. Funcionalidades nuevas

> Se observan pocos cambios sobre código previo para dos de las tres nuevas funcionalidades, esto es
> positivo. Siempre es deseable diseñar el código de forma tal que nuevas funcionalidades no requieran
> deployar servicios que ya estaban productivos.

#### Funcionalidad 1: Mejores N de M rondas

> La selección usa N y M del reglamento activo al generar la tabla, no el de la versión con que se
> jugaron las rondas. Rondas jugadas con "mejores 2 de 3" (v1, objetivos 5/5/1 → 50/50/10) y una v2
> "mejores 3 de 3" publicada antes de generar dan 110 en lugar de 100.

**Estado: Resuelta** — cada ronda jugada lleva la `RoundPolicy` (selección de rondas y agregación de
intentos) de la versión del reglamento con que se capturaron sus corridas:
`CategoryScoringService.collect` carga el reglamento por la versión de cada corrida y
`TeamRounds.explain` agrupa por desafío y selección histórica N/M. La misma selección conserva un solo cupo entre versiones;
`BestRoundsStandingsTest.theSameBestRoundsSelectionSpansRulebookVersions` verifica 50 + 50 + 10 → 100. El reglamento vigente sólo aporta los
desempates. Ver `DESIGN.md` 3.6.

- Test del caso exacto de la corrección (rondas 5/5/1 con "mejores 2 de 3", "mejores 3 de 3" publicada
  **antes** de generar → 100, la tercera ronda descartada):
  `BestRoundsStandingsTest.aRulebookPublishedBeforeGeneratingDoesNotChangeTheBestRoundsOfTheRoundsAlreadyPlayed`.
- El recálculo posterior también conserva la política original:
  `BestRoundsStandingsTest.aRecalculationKeepsTheBestRoundsOfTheRulebookThatGeneratedTheStandings`.

#### Funcionalidad 3: Desafío mixto

> Las evaluaciones del jurado no se pueden corregir. La única vía de corrección auditada es
> `AcceptAppeal`, y su comando solo trae mediciones e incidentes, un error de carga del panel queda
> fijo para siempre.

**Estado: Resuelta** — ver `DESIGN.md` 4.3.

- El pedido de corrección es un `ResultAmendment` con un `EvaluationsChange` sellado
  (`KeepEvaluations` / `ReplaceEvaluations`), compartido por las dos vías de corrección.
- `AcceptAppeal` acepta `correctedEvaluations` (opcional en la API: si falta, se conservan).
- Nueva vía sin apelación para errores de carga: puerto `CorrectResult`,
  `CorrectResultUseCase` y `POST /runs/{runId}/corrections`. La corrección queda en el historial con
  origen `DATA_ENTRY_ERROR` (`CorrectionOrigin`: `AcceptedAppeal` / `DataEntryError`), se audita y
  recalcula la tabla. Con la tabla `FINAL`, el único camino es una apelación.
- `RunResult.applyCorrection` valida las evaluaciones corregidas contra los jueces del turno.
- Pruebas: `MixedChallengeTest.onceTheStandingsAreFinalAResultCanOnlyChangeThroughAnAppeal`,
  `AppealApiTest.acceptsAnAppealThatCorrectsWhatTheJudgesEvaluated`,
  `AppealApiTest.correctsADataEntryErrorWithoutAnAppeal`, `StandingsTest`.
- Documentación: README, sección «Corrección por error de carga».

---

## 3. Persistencia

> No existe persistencia real, era requisito de la entrega contar con una capa de persistencia para una
> implementación end-to-end.

**Estado: Resuelta** — adaptadores JPA para todos los repositorios, `AuditLog` e `IdGenerator`
(`infrastructure.persistence`), esquema con Flyway, PostgreSQL con `docker compose` y el perfil
`postgres`, y H2 en modo PostgreSQL para los tests. Cada caso de uso es una transacción mediante el
decorador `TransactionalUseCases`, sin `@Transactional` en el núcleo. Los adaptadores JPA heredan los
tests de contrato (`JpaRunResultRepositoryTest`, `JpaStandingsRepositoryTest`) y la demo corre sobre
JPA (`JpaDemoScenarioTest`). Ver `DESIGN.md` 10.7 y `architecture.md`, sección «Persistencia».

---

## 4. Observaciones

> Separan el módulo core del módulo de backend. Conceptualmente ambos son parte del back-end de una
> aplicación. Esto tiene la desventaja de que no se pueden diseñar limitaciones de imports entre
> módulos, ya que el backend siempre va a importar desde el dominio, pero quizás queremos limitar los
> imports entre módulos del dominio, o lo que en DDD serían "bounded contexts".

**Estado: Resuelta** — el núcleo se dividió en seis contextos acotados
(`shared ← enrollment ← rules ← scheduling ← results ← appeals`). Cada uno sólo conoce a los de su
izquierda y, cuando necesita algo de uno a su derecha, declara un puerto que el otro implementa. Los
límites los verifica ArchUnit (`ContextBoundariesTest`, `BackendBoundariesTest`) y un import indebido
hace fallar `mvn verify`. Se conservan los dos módulos Maven porque garantizan al compilar que el
núcleo no ve Spring. Ver `DESIGN.md` 1.5.

---

## 5. Residuos detectados al revisar las correcciones

#### `Optional` como campo en `CalculateRunScore.RunScore`

Misma observación que en ambas entregas: el resultado del puerto guardaba
`Optional<SourcedScore> bySource` y `Optional<BonusCapOutcome> bonusCap`.

**Estado: Resuelta** — `RunScore` pasó a ser una clase que guarda el desafío de la versión fijada en la
corrida y su contexto de puntaje (siempre presentes) y obtiene de ellos `breakdown()`, `bySource()` y
`bonusCap()`; los dos últimos devuelven `Optional`. Desaparecieron
`CategoryScoringService.scoreBySource` y `bonusCapOutcome`, que quedaron sin cliente. El contrato JSON
de `GET /runs/{runId}/score` no cambió. Ver `DESIGN.md` 4.6 y
`CalculateRunScoreUseCaseTest.identifiesTheRunItsTeamItsChallengeAndTheRulebookVersionPinnedInIt`.

#### Regla de negocio en `CorrectResultUseCase`

"Con la tabla `FINAL`, un resultado sólo cambia por una apelación" se decidía con un `if` en el caso de
uso, contra el criterio de la Entrega 1 (los casos de uso coordinan, no deciden).

**Estado: Resuelta** — la decide `Standings.requireOpenToDataEntryCorrections(runId)`; el caso de uso
sólo carga la última revisión y la invoca. Ver `DESIGN.md` 4.12 y
`StandingsTest.aDataEntryErrorCanBeCorrectedOnlyWhileTheStandingsAreProvisional`.

#### Documentación desactualizada

**Estado: Resuelta** — `DESIGN.md` 4.3 y 4.13 decían que las evaluaciones no se corrigen y que toda
corrección nace de una apelación; 5.8 dejaba la persistencia fuera de alcance; 1.1 y 1.3 no reflejaban
los contextos ni los adaptadores JPA. El README no documentaba `POST /runs/{runId}/corrections` ni
`correctedEvaluations`.
