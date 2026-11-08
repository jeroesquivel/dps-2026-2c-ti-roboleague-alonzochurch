# Plan: API GraphQL de consulta (Entrega 3)

Exponer el dominio de consulta de RoboLeague mediante una API GraphQL que conviva con la API REST
existente, como propuesta de valor de la Entrega 3 (`roboleague-entrega-3.pdf`). Los resolvers son un
adaptador de entrada más: delegan en puertos de entrada del núcleo y no duplican reglas de negocio.

Estado: **aprobado**. Se ejecuta una etapa a la vez; cada etapa se revisa y se commitea antes de
empezar la siguiente.

## 1. Fuentes de verdad

- [`AGENTS.md`](../../AGENTS.md), [`architecture.md`](../../architecture.md) y
  [`DESIGN.md`](../../DESIGN.md).
- Skills [`evolve-core`](../../.agents/skills/core/evolve-core/SKILL.md) para los puertos nuevos y
  [`add-rest-endpoint`](../../.agents/skills/backend/add-rest-endpoint/SKILL.md) como referencia del
  recorrido de un adaptador de entrada.
- La spec `SPEC_api-graphql.md` (etapa 2), antes de escribir resolvers.

## 2. Requisitos de la consigna

| Elemento | Requisito |
|---|---|
| Esquema | Competencias, categorías, equipos, rondas, resultados, ranking y `ScoreBreakdown` |
| Consultas anidadas | Equipo → resultados por ronda → desglose del puntaje, en una sola consulta |
| Implementación | Resolvers que delegan en los casos de uso; sin lógica de negocio en la capa GraphQL |
| N+1 | Identificado y tratado en al menos un punto (DataLoader), con evidencia |
| Comparación con REST | Documento con dos casos: qué pide cada cliente, cuántas peticiones, datos de más o de menos |
| Pruebas | Consultas principales y al menos un error (campo inexistente, entidad no encontrada), en CI |
| `DESIGN.md` | Decisiones, alternativas descartadas y patrones no aplicados |

## 3. Decisiones tomadas

| Decisión | Resultado |
|---|---|
| Librería | Spring for GraphQL (`spring-boot-starter-graphql`), integración oficial de Spring Boot 4 |
| Lotes | `java-dataloader` (incluido), con **carga por lotes y caché por petición** |
| Pruebas | `spring-graphql-test` (`HttpGraphQlTester`); dobles escritos a mano, sin framework de mocks |
| Demo | GraphiQL incluido en Spring for GraphQL; sin cliente GraphQL en el frontend |
| Estadísticas de Hibernate | Descartadas: la evidencia del N+1 es un decorador contador en los tests |
| Subscription (opcional) | No se implementa; se documenta como patrón no aplicado (Observer / eventos de dominio, ver `DESIGN.md` 5.3) |
| Alcance | Solo lectura: las escrituras siguen siendo REST |
| Puertos nuevos | Devuelven agregados inmutables, como los `Find*` existentes (sin modelo de lectura aparte: `DESIGN.md` 5.2) |

## 4. Diseño

```text
cliente GraphQL --HTTP /graphql--> adapter.in.graphql (resolvers + mappers + tipos propios)
                                         | solo puertos de entrada (<contexto>.port.in)
                                         v
                                   *UseCase -> repositorios del dominio
```

- `backend/src/main/resources/graphql/schema.graphqls`: esquema primero (API First).
- `backend/.../adapter/in/graphql/{competition,team,round,run,standings,error}`: un `@Controller`
  por tipo GraphQL y un `*GraphMapper` con records propios. No se reutilizan DTOs ni mappers de
  `adapter.in.web`: los adaptadores no se conocen entre sí.
- Errores: un `DataFetcherExceptionResolverAdapter` traduce la jerarquía del dominio
  (`NotFoundException` → `NOT_FOUND`, `InvalidValueException` → `BAD_REQUEST`, …) con el mismo `code`
  que usa REST, sin stack traces.
- Escalar `Points` escrito a mano (BigDecimal sin pérdida de precisión).

### 4.1 Patrones

| Patrón / principio | Dónde |
|---|---|
| Adaptador de entrada (hexagonal) | Resolvers |
| DIP | Resolvers → interfaces `port.in` |
| ISP | Puertos de lote separados de los de un elemento |
| Carga por lotes y caché por petición | DataLoader de equipos |
| Decorator | `TransactionalUseCases` (existente) y el contador de los tests |
| Traducción de excepciones | `GraphQlExceptionResolver` |

### 4.2 Esquema (borrador)

```graphql
type Query {
  competition(id: ID!): Competition
  team(id: ID!): Team
  round(id: ID!): Round
  run(id: ID!): Run
  standings(competitionId: ID!, categoryId: ID!): Standings
}
type Competition   { id, name, period, activeRulebookVersion, categories: [Category!]! }
type Category      { id, name, ageRange, robotClass, teams: [Team!]!, rounds: [Round!]!, standings: Standings }
type Team          { id, name, status, robot: Robot!, category: Category!, results: [RoundResult!]! }
type RoundResult   { round: Round!, runs: [Run!]! }
type Round         { id, ordinal, challengeId, rulebookVersion, heats: [Heat!]!, runs: [Run!]! }
type Run           { id, attempt, status, team: Team!, round: Round!, score: ScoreBreakdown! }
type ScoreBreakdown { completion, missingSources, total: Points, contributions: [Contribution!]!, bonusCap, bySource }
type Standings     { revision, status, rulebookVersion, generatedAt, entries: [StandingEntry!]! }
type StandingEntry { position, team: Team!, totalPoints: Points!, appliedTiebreaks, explanation }
scalar Points
```

No se expone: mutaciones, datos personales de los integrantes (menores), documentación, motivos de
rechazo, auditoría, temporadas, apelaciones ni paginación.

### 4.3 Puertos nuevos del núcleo

| Puerto | Contexto | Repositorio | Cliente |
|---|---|---|---|
| `FindCategoryTeams` | enrollment | `TeamRegistrationRepository.findByCategory` (nuevo) | `Category.teams` |
| `FindTeamRegistrations` (lote) | enrollment | `TeamRegistrationRepository.findAllById(Set)` (nuevo) | DataLoader: `StandingEntry.team`, `Run.team` |
| `FindCategoryRounds` | scheduling | `RoundRepository.findByCategory` (existe) | `Category.rounds` |
| `FindRoundRuns` | results | `RunResultRepository.findByRound` (existe) | `Round.runs` |
| `FindTeamRuns` | results | `RunResultRepository.findByTeam` (nuevo, índice en Flyway `V2`) | `Team.results` |

Cada método nuevo de repositorio se implementa en memoria y en JPA y se cubre con su test de contrato.

### 4.4 N+1

- Tratado: `standings { entries { team { … } } }` y `Run.team`. Con DataLoader, una llamada a
  `FindTeamRegistrations` y un `SELECT … IN` por nivel; la caché por petición evita volver a cargar un
  equipo que reaparece en otro nivel de la misma consulta.
- Evidencia: test con un decorador contador escrito a mano (1 llamada de lote, 0 individuales).
- Deuda documentada: `Run.score` (carga el reglamento por corrida) y `Team.results → round`.
- Limitación: cada resolver abre su propia transacción; una consulta anidada no lee una foto única.

## 5. Etapas

1. **Correcciones pendientes:** `Optional` como campo en `CalculateRunScore.RunScore`; test del caso
   exacto de F1 (reglamento publicado antes de generar); `DESIGN.md` 4.3 y 5.8 y sección de contextos
   con ArchUnit; README de `POST /runs/{runId}/corrections`; `.docs/correcciones-entrega-2.md`.
2. `SPEC_api-graphql.md`.
3. Dependencia, esquema, `competition(id)` con categorías, traductor de errores, reglas de ArchUnit y
   tests (campo inexistente y `NOT_FOUND`).
4. `Category.teams` y `standings`, con puertos y persistencia nuevos.
5. DataLoader de equipos y evidencia del N+1.
6. `team → results → round → run → ScoreBreakdown`.
7. `.docs/graphql-vs-rest.md`, README, `DESIGN.md` (sección 11), `architecture.md` y skill
   `.agents/skills/backend/add-graphql-resolver`.
8. `mvn clean verify` y auditoría con `.agents/agents/auditor.md`.

## 6. Fuera de alcance

Mutaciones, subscriptions, cliente GraphQL en el frontend, paginación, autenticación y cualquier
funcionalidad de negocio nueva.
