# Decisiones de diseño

Este documento enumera las decisiones de diseño del módulo de dominio de RoboLeague: qué patrón o
principio se aplicó, dónde y por qué, qué alternativas se descartaron y qué patrones se decidió no
aplicar junto con sus consecuencias.

## 1. Arquitectura general

### 1.1 Clean Architecture en tres capas con dependencias hacia adentro

**Patrón / principio:** Clean Architecture, Regla de Dependencia, Inversión de Dependencias (DIP).

**Dónde:** organización de paquetes bajo `com.dps.roboleague`.

| Capa | Paquete | Contenido | Depende de |
| --- | --- | --- | --- |
| Dominio | `domain.*` | Entidades, value objects, reglas, servicios de dominio y **todos los puertos**: las interfaces de los casos de uso (`domain.port.in`) y las de repositorios, `AuditLog` e `IdGenerator` (en el paquete de cada agregado) | Nada fuera del dominio y del JDK |
| Aplicación | `application.usecase` | Interactors (`*UseCase`) que implementan los puertos de entrada y coordinan | Dominio |
| Detalles | `infrastructure.*`, `demo`, `Main` | Adaptadores en memoria, composition root y ejecución de ejemplo | Aplicación, dominio y otros componentes de detalles |

El dominio no importa ninguna clase de `application` ni de `infrastructure`: la dirección de las
dependencias es siempre hacia el centro. Las reglas de negocio (puntaje, elegibilidad, desempates,
conflictos de agenda, unicidad de intentos y de rondas, plazos de apelación, cierre de posiciones)
quedan expresadas en clases que no conocen persistencia, frameworks ni una interfaz externa de
entrada/salida. Los casos de uso sólo cargan agregados, invocan al dominio, persisten y auditan
(ver 4.12).

**Por qué:** la consigna explicita que lo determinante es el modelado y la extensibilidad. Aislar el
negocio permite agregar una API REST y persistencia real mediante adaptadores nuevos sin cambiar
las reglas del dominio, siempre que se conserve su significado. La integración también deberá
resolver representación, transacciones y concurrencia; las interfaces no implementan esas garantías
(ver sección 8).

**Alternativas descartadas:** una implementación por capas (`controller → service → dao`) que
acople la lógica a repositorios concretos; y un único paquete plano, que no comunica la separación
entre negocio y detalles. Una arquitectura por capas también puede invertir sus dependencias:
lo determinante es el acoplamiento efectivo, no el nombre de las capas.

### 1.2 Casos de uso como puertos de entrada con entradas propias

**Patrón / principio:** Ports & Adapters, Interface Segregation (ISP), Single Responsibility (SRP).

**Dónde:** `domain/port/in/*` (interfaces) y `application/usecase/*UseCase` (implementaciones).

Las interfaces de los casos de uso son parte del negocio: describen qué operaciones ofrece el
módulo y con qué datos, así que viven en el dominio (`domain.port.in`). Los interactors de
`application` las implementan; un controller futuro depende de la interfaz, nunca del interactor.

Cada caso de uso es una interfaz con un único método `execute`. Las operaciones con varias entradas
las agrupan en un `record Command` anidado; por ejemplo `CaptureRunResult.Command`. Las búsquedas
`FindCompetition`, `FindRunResult`, `FindRound`, `FindTeamRegistration` y `FindAppeal` reciben
directamente su identificador tipado: `FindRunResult.execute(RunId)`. La implementación vive aparte
y consulta los repositorios a través de puertos de salida.

**Por qué:** un consumidor (mañana un controller REST) depende exclusivamente de la operación que
necesita. El comando anidado mantiene junta la operación con su contrato de entrada; recibir
directamente un identificador evita un objeto intermedio que no agrega información ni validaciones.
Ambas formas conservan el puerto de entrada y la separación de responsabilidades.

Leer también es un caso de uso: `FindCompetition`, `FindRunResult`, `FindRound`,
`FindTeamRegistration`, `FindAppeal`, `GetStandings` y `FindAuditTrail` son puertos de entrada.
En este diseño evitan que la demo o un futuro controller consulten repositorios directamente.

**Las consultas ya no exponen agregados mutables.** `FindRound`, `FindRunResult`,
`FindTeamRegistration` y `FindAppeal` devuelven el agregado, pero los agregados son inmutables
(ver 4.11): `resolveWith`, `schedule`, `applyCorrection`, `accept` y `reject` devuelven una
instancia nueva que sólo un caso de uso persiste. Quien consulta no puede aceptar una inscripción
ni corregir un resultado salteando el caso de uso y la auditoría.

**Usuarios de las consultas.** Hoy sus únicos consumidores son los tests y la demo. Se conservan
porque responden a necesidades de un usuario real de la Entrega 2: un equipo consulta el estado y
los motivos de rechazo de su inscripción, el resultado de su corrida antes de apelar y el estado
de su apelación; el organizador consulta la agenda de una ronda. Si al diseñar la API alguna no
tiene un consumidor externo, deja de ser un caso de uso y se elimina.

**Por qué los de consulta también:** el ejecutable de ejemplo y los tests son adaptadores de
entrada. Mantener sus consultas detrás de esos contratos ofrece un camino explícito por la capa de
aplicación. Es una convención arquitectónica, no una barrera de seguridad que impida instanciar un
repositorio público por fuera del composition root.

Los puertos de consulta contienen las operaciones y sus datos de salida. La transformación de
eventos de auditoría a acciones, usada sólo por los tests, vive en `support/TestEdition.actionsOf`.
`FindCompetition.View` proyecta los datos de la competencia y sus categorías. Por separado,
`CreateCompetition.execute` devuelve un `Result` con `competitionId` y `categoryIds`; su método
`firstCategory()` lo usan la demo y `TestEdition`. Así la creación ya entrega los identificadores
necesarios para continuar el flujo sin hacer una consulta adicional.

Los comandos no tienen todos la misma forma: `CalculateRunScore` conserva `Command(RunId)`, mientras
que las cinco búsquedas por ID enumeradas arriba reciben el identificador directamente. Son contratos
actuales de operaciones distintas, no una obligación de envolver o desenvolver toda entrada simple.

**Alternativas descartadas:** concentrar responsabilidades sin relación en un servicio y obligar a
todos los consumidores a depender de sus operaciones. Tener varios métodos relacionados no viola
SRP ni ISP por sí solo; aquí se eligieron contratos pequeños por operación. También se descartaron
los comandos que sólo envolvían los identificadores de búsqueda, pasar muchos parámetros sueltos en
operaciones complejas y exponer los repositorios desde el composition root. Un comando agrupa la
entrada, pero agregarle componentes también cambia su constructor y puede exigir adaptar clientes.

### 1.3 Repositorios declarados por el dominio e implementados afuera

**Patrón / principio:** Repository, DIP.

**Dónde:** cada interfaz vive en el paquete de su agregado —`domain/competition/SeasonRepository`
y `CompetitionRepository`, `domain/rulebook/RulebookRepository`, `domain/team/TeamRegistrationRepository`,
`domain/schedule/RoundRepository`, `domain/result/RunResultRepository`,
`domain/ranking/StandingsRepository`, `domain/appeal/AppealRepository`— igual que
`domain/audit/AuditLog` y `domain/shared/IdGenerator`. Las implementaciones están en
`infrastructure/memory/*` e `infrastructure/id/*`.

El gateway pertenece al negocio y sólo su implementación es un detalle: el dominio es dueño de sus
repositorios. Por eso un servicio de dominio puede depender de ellos (`CategoryScoringService`, ver
4.8) sin violar la regla de dependencia. Ubicarlos junto a su agregado, en lugar de en un paquete
`port.out` común, evita un ciclo entre ese paquete y los agregados que nombra.

Los repositorios están expresados en el lenguaje del negocio (`findLatest(competitionId,
categoryId)`, `findByRun(runId)`) y exponen agregados, `Optional` y listas, nunca filas ni
estructuras de base de datos. No tienen métodos sin cliente: `RulebookRepository.findLatest`
desapareció cuando la versión siguiente pasó a calcularse desde la competencia (ver 3.1).

Cuando el contrato de un puerto incluye una regla de negocio se verifica con un test de contrato
abstracto que hereda cada adaptador:

- `StandingsRepositoryContractTest` —"publicar reemplaza la revisión provisional, las anteriores no
  se tocan"—, heredado por `InMemoryStandingsRepositoryTest`;
- `RunResultRepositoryContractTest` —"no se guardan dos corridas para el mismo intento de un equipo
  en una ronda"—, heredado por `InMemoryRunResultRepositoryTest` (ver 4.12).

Un adaptador futuro debería reutilizarlos y sumar sus pruebas de integración. La firma del puerto
por sí sola no impone esas políticas: el test fija el comportamiento esperado.

**Por qué:** el dominio define los contratos que necesita en sus propios términos, y la
infraestructura los implementa. Las pruebas de integración ejercitan adaptadores en memoria reales,
sin base de datos; también hay un escenario con dobles manuales (ver 5.5).

**Alternativas descartadas:** un `Repository<T, ID>` genérico con CRUD uniforme, para no imponer
operaciones que estos casos de uso no necesitan. Un repositorio genérico no rompe la arquitectura
por sí mismo, pero aquí no justificaba abstraer las consultas específicas. También se descartó usar las
implementaciones concretas directamente en los casos de uso, que ataría el negocio al detalle.

### 1.4 Composition root explícito, sin framework de inyección

**Patrón / principio:** Composition Root, Inyección de dependencias por constructor.

**Dónde:** `infrastructure/config/RoboLeagueCompositionRoot`.

Es el lugar de ensamblado donde se eligen los adaptadores concretos. Los casos de uso reciben sus
colaboradores por constructor; los agregados son inmutables y sus transiciones devuelven instancias
nuevas (ver 4.11). **Sus métodos de acceso a casos de uso sólo devuelven puertos de entrada:** no hay
getters de repositorios. Algunas consultas devuelven agregados del dominio; como no se pueden mutar,
no abren un camino alternativo a los casos de uso, pero un futuro adaptador de API igual deberá
proyectar su salida a DTOs para no acoplar el contrato público al modelo interno.

`acceptAppealUseCase()` recibe la instancia de `recalculateStandingsUseCase()`: aceptar una apelación
dispara el recálculo a través del mismo puerto de entrada (ver 4.5).

**Por qué:** deja ver de un vistazo el grafo completo del sistema y permite que los tests construyan
el módulo con un `Clock` fijo. Además prueba que el dominio funciona sin contenedor.

**Alternativas descartadas:** Spring u otro contenedor (dependencia pesada e innecesaria para una
entrega de dominio); repositorios con estado mutable global o singletons compartidos (dificultan
aislar el estado entre tests). Sí existen constantes y fábricas estáticas sin ese estado compartido,
como `RoboLeagueCompositionRoot.inMemory`.

## 2. Modelado del puntaje

### 2.1 Reglas de puntaje como estrategias intercambiables

**Patrón / principio:** Strategy, Open/Closed (OCP).

**Dónde:** `domain/scoring/ScoringRule` y las implementaciones de `domain/scoring/rule`:
`TimeScoringRule`, `ObjectiveScoringRule`, `PrecisionScoringRule`, `ResourceScoringRule`,
`JudgePanelScoringRule`, `PenaltyScoringRule` y `ThresholdBonusRule`.

El enunciado describe desafíos puntuados por tiempo, objetivos, precisión, consumo de recursos,
evaluación de jueces o combinaciones. Cada criterio es una clase que implementa la misma interfaz y
recibe su configuración por constructor (referencia de tiempo, puntos por objetivo, tolerancia de
consumo).

**Por qué:** un criterio nuevo implementa `ScoringRule` y se incorpora a la configuración y las
pruebas; el algoritmo que consume reglas no necesita conocer esa clase nueva. Cada regla actual
mantiene su configuración inmutable y se puede probar de forma aislada.

Las siete son `record` con configuración inmutable en las implementaciones actuales.
`PenaltyScoringRule.of` construye su catálogo con `Collectors.toMap` (un código repetido lanza
`InvalidValueException`), y el constructor canónico también hace `Map.copyOf`. La interfaz no fuerza
que una implementación futura conserve esa inmutabilidad (ver sección 8).

**La configuración protege su propia semántica.** Los parámetros de las reglas no son `Points` ni
`BigDecimal` sueltos, sino value objects que no admiten valores negativos (ver 4.6):

| Concepto | Tipo | Dónde |
| --- | --- | --- |
| Coeficiente "puntos por unidad" | `PointsRate` | `pointsPerSecondSaved`, `pointsPerObjective`, `pointsPerUnitOver`, `JudgePanelScoringRule.weight` |
| Monto fijo configurado (tope, bono, deducción) | `PointsAmount` | `TimeScoringRule`/`PrecisionScoringRule.maximumPoints`, `ThresholdBonusRule.bonus`, `PenaltyDefinition.deduction` |
| Umbral o margen sobre una medición | `MetricValue` | `ResourceScoringRule.allowance`, `ThresholdBonusRule.threshold` |

Así una "penalización" por consumo no puede configurarse con un coeficiente negativo que sume puntos,
ni un bono o un peso negativos: el reglamento no se puede construir. `TimeScoringRule` exige además
un tiempo de referencia positivo.

**Alternativas descartadas:** un método de cálculo con `switch` sobre un `enum` de tipos de desafío
(cada criterio nuevo obliga a editar el mismo método, en lugar de extenderlo mediante una regla);
una clase base abstracta de puntaje sin comportamiento común que justificara esa jerarquía. La
composición actual sólo requiere implementar `ScoringRule`.

### 2.1.1 El contrato de `ScoringRule` es uno solo para todas las implementaciones

**Patrón / principio:** Liskov Substitution (LSP), diseño por contrato.

**Dónde:** `domain/scoring/ScoringRule` y las siete implementaciones de `domain/scoring/rule`.

`ScoringRule` declara sólo `apply(ScoringContext)` y `referencedMetrics()` (las métricas que la
regla lee, ver 2.2); no incluye una especificación textual del contrato ni lo fuerza a nivel de
tipos. El helper por defecto `breakdownFor` se eliminó: ningún código de producción lo usaba, sólo
los tests, y un método `default` invita a implementaciones que no lo necesitan (ver 5.9). El comportamiento
común implementado y comprobado para las siete reglas actuales es: **con un contexto válido, emitir
al menos una contribución explicada y devolver cero ante la ausencia de la medición, evaluación o
incidente que corresponda**, en lugar de lanzar por esa ausencia.

**Por qué:** un cliente puede aplicar una `ScoringRule` sin conocer su implementación. Para conservar
la sustituibilidad respecto del comportamiento esperado, una regla nueva debe respetar ese manejo
de datos ausentes. Un test parametrizado en
`ScoringRulesTest` recorre las siete reglas simples de su proveedor `everyRule()` y verifica el
contrato. Para comprobar una regla nueva hay que agregarla explícitamente a ese proveedor.

**Consecuencia sobre dónde se valida:** tolerar datos ausentes al puntuar no reemplaza las validaciones
del desafío. Las invocan los propios agregados: `RunResult.capture` (al capturar) y
`RunResult.applyCorrection` (al corregir) llaman a `ChallengeSpec.validate`, `validateIncidents` y,
al capturar, `validateEvaluations`. La fuente de configuración del catálogo es
`ChallengeSpec.penalties`; `PenaltyScoringRule` sí guarda una copia indexada del catálogo recibido
para calcular deducciones (ver 2.4). Un incidente desconocido se rechaza en esos flujos, aunque
aplicar la regla de penalización directamente devuelve cero con explicación.

**El contrato alcanza a la configuración.** Una regla con parámetros negativos cumpliría la firma
pero rompería la semántica de `ContributionKind` (una contribución `PENALTY` que suma). Por eso los
parámetros son `PointsRate`/`PointsAmount`/`MetricValue`, que no pueden ser negativos (ver 2.1), y
`ScoringRulesTest.penaltyContributionsNeverAddPoints` verifica que ninguna regla de `everyRule()`
emite una penalización positiva.

**Alternativas descartadas:** dar a cada regla un manejo incompatible de la ausencia de datos;
declarar una excepción chequeada en la firma sin acordar qué significa el resultado de la operación;
validar el signo de cada parámetro en el constructor de cada regla, repitiendo la misma regla en
siete lugares.

### 2.2 Un desafío conoce una lista de reglas, no una regla que a su vez es una lista

**Patrón / principio:** simplicidad deliberada; se prefirió sobre Composite (ver 5.10).

**Dónde:** `ChallengeSpec.scoringRules` y `ChallengeSpec.score`.

`ChallengeSpec` guarda directamente una `List<ScoringRule>`. Al puntuar, `score` aplica cada
regla de la lista, le suma las contribuciones de `PenaltyScoringRule.of(penalties)` (armada
desde el catálogo del propio desafío, ver 2.4) y devuelve un único `ScoreBreakdown` con todo.

**El desafío valida su propia configuración al construirse**, no al puntuar:

- una métrica no puede declararse dos veces, aunque sea con distinto `MetricKind`;
- un código de penalización no puede declararse dos veces (antes fallaba recién al puntuar, con una
  `IllegalStateException` desde `PenaltyScoringRule.of`);
- toda métrica que una regla declara en `referencedMetrics()` tiene que estar definida en el desafío,
  así una regla sobre una métrica inexistente no aporta cero en silencio.

Las tres violaciones lanzan `InvalidValueException` desde el constructor de `ChallengeSpec`.

**Por qué:** el único lugar de producción que combina varias `ScoringRule` es este método.
Un `CompositeScoringRule` obligatorio alrededor de la lista no evitaría duplicación entre
consumidores: el desafío igualmente debe incorporar las deducciones de su propio catálogo (2.4).
La lista directa deja un único punto de combinación, sin un wrapper intermedio.

**Alternativas descartadas:** un Composite explícito obligatorio para el puntaje (ver 5.10).

### 2.3 Puntaje explicable: el resultado es un desglose, no un número

**Patrón / principio:** Value Object, Tell Don't Ask.

**Dónde:** `domain/scoring/ScoreContribution`, `ScoreBreakdown` y el retorno de
`CalculateRunScore.RunScore`.

Cada regla devuelve una lista de `ScoreContribution` con código, explicación textual y puntos aportados;
el total es la suma de las contribuciones. Las reglas emiten contribución incluso cuando aportan
cero ("bonificación no otorgada", "consumo dentro del margen"), porque la ausencia de puntos también
es información que el juez necesita ver.

**Por qué:** el requisito de "cálculo explicable" exige poder mostrar cómo se llegó a cada puntaje.
Si el dominio devolviera un `BigDecimal`, la explicación habría que reconstruirla afuera, duplicando
las reglas.

**Alternativas descartadas:** loguear el cálculo (la explicación queda fuera del modelo y no es
consultable); recalcular la explicación en la capa de presentación (duplica las reglas y se
desincroniza).

### 2.3.1 La naturaleza de una contribución es un concepto del dominio

**Patrón / principio:** desacoplamiento de implementaciones concretas, clasificación semántica tipada.

**Dónde:** `domain/scoring/ContributionKind` (`EARNED`, `BONUS`, `PENALTY`), usado por
`ScoreBreakdown.totalOf` y consultado por `TeamScoreSummary.penaltyPoints`.

Cada contribución declara qué representa, con independencia de qué regla la produjo. El desempate por
penalizaciones pregunta `breakdown.totalOf(ContributionKind.PENALTY)`.

**Por qué:** el ranking debe reconocer las deducciones por su significado, no por el código de una
implementación. Con `ContributionKind`, el paquete `ranking` no importa nada de `scoring.rule`.
Además de los incidentes de `PenaltyScoringRule`, `ResourceScoringRule` etiqueta sus deducciones como
`PENALTY`, por lo que ambas cuentan en el desempate sin que éste tenga que conocer esas clases.

**Consecuencia adicional:** la publicación puede separar lo ganado de las bonificaciones y de las
penalizaciones sin conocer ninguna regla concreta, que es lo que el requisito de explicabilidad pide.

**Alternativas descartadas:** dejar el código de regla como `String` y seguir comparando textos
(un contrato basado en texto permitiría errores como `totalFor("PENALTIE")`); reconocer todas las
penalizaciones mediante una lista de códigos concretos. El contrato actual de `totalFor` recibe
`ScoringRuleCode`, mientras que `totalOf` consulta la naturaleza de la contribución.

### 2.4 Penalizaciones y bonificaciones como parte del mismo mecanismo

**Patrón / principio:** uniformidad de modelo.

**Dónde:** `PenaltyDefinition`, `IncidentReport`, `PenaltyScoringRule`, `ThresholdBonusRule` y
`ChallengeSpec.penalties`.

Las reglas representan las deducciones con contribuciones negativas y los bonos otorgados con
contribuciones positivas, según sus parámetros configurados; cuando no se aplican emiten cero.
Todas aparecen en el mismo desglose, etiquetadas con su `ContributionKind`. Ese tipo es una
clasificación semántica: `ScoreContribution` no impone por sí mismo el signo de los puntos.

El catálogo de penalizaciones es un componente de `ChallengeSpec`, no un parámetro escondido dentro
de la regla de puntaje, porque cumple dos funciones: `ChallengeSpec.validateIncidents` rechaza al
capturar un incidente que el reglamento no define, y `ChallengeSpec.score` arma con él la
`PenaltyScoringRule` que aplica las deducciones. Una sola fuente de verdad para las dos cosas.

**Por qué:** el desglose queda completo y auditable en una sola estructura, y el total ya contempla
ajustes sin pasos posteriores.

**Alternativas descartadas:** aplicar las penalizaciones después del cálculo, como un descuento sobre
el total, lo que las dejaría fuera de la explicación y obligaría a un orden implícito de aplicación.

## 3. Reglamento, versionado y recálculo

### 3.1 Reglamento inmutable y versionado

**Patrón / principio:** Value Object inmutable, fábrica estática.

**Dónde:** `domain/rulebook/Rulebook`, `RulebookDraft`, `RulebookVersion` y `Rulebook.of`.

Un `Rulebook` reúne los desafíos, los requisitos de elegibilidad (4.1), la política de agregación de
intentos (3.5), los criterios de desempate y el plazo de apelación (`AppealWindow`, ver 4.5) de una
versión. El contenido sin versión es un `RulebookDraft`: valida al construirse que haya al menos un
desafío y que ningún identificador se repita (un desafío no puede pisar en silencio a otro;
`InvalidValueException`). `Rulebook.of(competitionId, version, publishedOn, draft)` lo indexa por
identificador y fija la versión. El constructor mantiene las validaciones y las copias defensivas.

**La competencia nace con su reglamento.** `CreateCompetition.Command` recibe el `RulebookDraft`
inicial: `CreateCompetitionUseCase` crea la competencia con la versión `v1` activa y guarda ese
reglamento, auditando `COMPETITION_CREATED` y `RULEBOOK_PUBLISHED`. Ya no existe una competencia sin
reglamento: `Competition.activeRulebookVersion()` devuelve un `RulebookVersion` (no un `Optional`),
desapareció `requireActiveRulebookVersion()` y con él la `ConflictException` "competition has no
published rulebook" que tenían que contemplar inscripción, programación y generación.

En el flujo normal, publicar un reglamento no modifica el anterior: `PublishRulebookUseCase` calcula
la versión siguiente con `competition.activeRulebookVersion().next()` (la activa siempre es la última,
porque `Competition.activateRulebook` sólo acepta versiones que la superen) y obtiene una competencia
nueva con esa versión activa (ver 4.11).

**Por qué:** el enunciado exige poder recalcular resultados con exactamente la versión de reglas
correspondiente. El record y sus copias defensivas evitan modificar sus colecciones; las estrategias
actuales conservan su configuración inmutable. La fábrica evita el estado intermedio de un builder
porque el caso de uso ya tiene todos los datos disponibles. Las interfaces de estrategia no fuerzan
inmutabilidad profunda y el almacenamiento no bloquea reemplazos de versiones (ver sección 8).

**Alternativas descartadas:** un builder para volver a reunir datos que ya llegan juntos; un
reglamento mutable con historial de cambios (cualquier corrección alteraría resultados ya
publicados); guardar sólo la versión vigente (haría imposible el recálculo histórico); repetir los
cinco componentes del reglamento en `CreateCompetition.Command` y `PublishRulebook.Command` en lugar
de un único `RulebookDraft`; crear la competencia sin reglamento y obligar a cada caso de uso a
verificar que ya se publicó uno.

### 3.2 La versión de reglas se fija en la ronda y viaja con el resultado

**Patrón / principio:** Snapshot de configuración.

**Dónde:** `Round.rulebookVersion`, `RunResult.rulebookVersion`, `domain/ranking/CategoryScoringService`.

Al programar una ronda se fija la versión vigente; al capturar un resultado esa versión se copia en
el `RunResult`. Puntuar una corrida siempre carga el reglamento por esa versión, no por la vigente.

**Por qué:** un reglamento publicado a mitad del evento no puede cambiar retroactivamente lo ya
corrido. El test `RulebookEvolutionTest` verifica justamente que una corrida vieja sigue
puntuando con su reglamento aunque exista una versión nueva activa.

**Alternativas descartadas:** resolver siempre el reglamento activo de la competencia (rompe el
recálculo determinista); guardar el puntaje calculado dentro del resultado (lo vuelve un dato
desincronizable y no explica de dónde salió).

### 3.3 Separación entre generar, publicar y recalcular

**Patrón / principio:** SRP, máquina de estados explícita.

**Dónde:** `GenerateStandingsUseCase`, `PublishStandingsUseCase`, `RecalculateStandingsUseCase`,
`domain/ranking/Standings` y `domain/ranking/StandingsHistory`.

`Standings` es inmutable y lleva número de revisión (`Revision`) y estado (`PROVISIONAL` / `FINAL`).
Su constructor es privado: las únicas formas de obtener una revisión son `Standings.provisional`
(revisión 1), `publish()` y `supersede()`, así no se puede armar una revisión `FINAL` sin publicar.
Por eso dejó de ser un `record` (el constructor canónico de un record no puede ser más restrictivo
que el tipo). `publish()` falla si ya es definitiva; `supersede()` abre una revisión nueva
provisional conservando la versión de reglamento. El repositorio conserva todas las revisiones.

**Las reglas sobre el historial las decide `StandingsHistory`**, la colección de revisiones de una
categoría (sólo admite revisiones de su competencia y categoría):

- `generate(version, at, entries)` crea la revisión 1 y lanza `ConflictException` si la categoría ya
  tiene posiciones, con un mensaje que indica usar el recálculo. Antes lo decidía
  `GenerateStandingsUseCase` consultando `findLatest(...).isPresent()`.
- `requireOpenForResults()` lanza `ConflictException` si alguna revisión del historial fue publicada
  como `FINAL`: una categoría con posiciones definitivas no admite corridas nuevas y sus resultados
  sólo cambian por apelación. `CaptureRunResultUseCase` la invoca antes de capturar. Mira todo el
  historial y no sólo la última revisión, para que un recálculo posterior (que vuelve a dejar la
  última revisión como provisional) no reabra la captura.

**Publicar exige que no haya apelaciones pendientes.** `PublishStandingsUseCase` reúne las
apelaciones de las corridas de la categoría (`CategoryScoringService.runsOf` +
`AppealRepository.findByRun`) y `Appeals.requireNonePending()` rechaza la publicación con
`ConflictException` mientras alguna siga `SUBMITTED` (ver 4.5).

**Recalcular reabre también posiciones `FINAL` (decisión explícita).** `supersede()` no distingue el
estado de la revisión vigente: si una apelación tardía, aceptada dentro de su plazo, corrige una
corrida de una categoría ya publicada, el recálculo agrega una revisión `n+1` `PROVISIONAL` y la
revisión `FINAL` anterior queda intacta en el historial. La tabla corregida vuelve a ser definitiva
sólo cuando se la publica otra vez, y para eso no puede quedar ninguna apelación pendiente. Se eligió
así porque el reglamento ya acota cuándo puede llegar una apelación (`AppealWindow`); prohibir el
recálculo de una tabla final dejaría una corrección aceptada sin efecto sobre las posiciones.

Al generar, la versión activa determina la agregación de intentos y los desempates de la tabla. Al
recalcular, se conserva la versión de la tabla anterior para ambos; cada corrida se puntúa por separado con su propia
versión fijada. No se fuerza a todas las corridas de una categoría a usar una única fórmula, ni se
congela el conjunto de resultados: el servicio vuelve a consultar las corridas disponibles.

**Por qué:** cubre "diferenciar resultados provisionales y definitivos" y "reprocesar posiciones
después de una corrección" sin que una operación pise silenciosamente a la otra, dejando trazable
cada publicación.

La política de revisiones —una fila por revisión, publicar reemplaza la provisional, las anteriores
se conservan— se verifica con `StandingsRepositoryContractTest`. `InMemoryStandingsRepository`
almacena un `TreeMap` por competencia y categoría, con el número de revisión como clave: `put`
reemplaza la misma revisión, `lastEntry` obtiene la vigente y sus valores ya están ordenados para
el historial. Las consultas devuelven listas inmutables copiadas del mapa. El contrato se mantiene
aunque las revisiones se guarden fuera de orden, sin eliminar elementos de una lista ni ordenarla
en cada lectura.

Esta conservación describe los flujos de publicación y recálculo. `save` es un upsert por revisión:
no impide que un llamador directo reemplace arbitrariamente una revisión anterior o definitiva.

**Alternativas descartadas:** una tabla mutable que se sobrescribe (pierde el histórico y no permite
comparar antes y después de una apelación); publicar automáticamente tras generar (impide revisar el
resultado provisional); dejar la regla de upsert implícita en cada adaptador, que hace que dos
implementaciones del mismo puerto signifiquen cosas distintas; prohibir recalcular una tabla `FINAL`
(una apelación aceptada no tendría efecto); bloquear la captura sólo si la *última* revisión es
`FINAL` (un recálculo reabriría la carga de corridas nuevas).

### 3.4 Desempates como cadena de comparadores configurable

**Patrón / principio:** Strategy + composición de `Comparator`.

**Dónde:** `domain/ranking/TiebreakRule` (extiende `Comparator<TeamScoreSummary>`), sus
implementaciones en `domain/ranking/rule` y `RankingService`.

`RankingService` arma el comparador final: puntaje total (según la agregación del reglamento, ver
3.5) descendente y luego, en orden, cada regla de
desempate del reglamento. Si ninguna regla separa a dos equipos, comparten posición y la siguiente
posición salta. El ID del equipo sólo da un orden determinista a los empates completos; no les
asigna posiciones distintas.

Cuando una entrada tiene el mismo total que la anterior y algún criterio las separa, su lista
`appliedTiebreaks` registra un `AppliedTiebreak` con el primer criterio que discrimina entre esas dos
entradas. La lista queda vacía para la primera entrada, ante totales distintos y ante un empate
completo. No es una traza de todas las comparaciones del ordenamiento.

`AppliedTiebreak` guarda código y descripción. Esta última distingue, por ejemplo, dos
`FastestMetricTiebreak` con métricas distintas pero el mismo código `FASTEST_METRIC`.
`FewestPenaltiesTiebreak` compara puntos deducidos de tipo `PENALTY`, no cantidad de incidentes.

**Por qué:** el orden de los criterios de desempate es una decisión del reglamento, no del código; y
registrar la regla aplicada mantiene la coherencia con el requisito de explicabilidad.

**Alternativas descartadas:** un comparador único con toda la lógica (no configurable por edición);
Chain of Responsibility con objetos propios (equivalente en comportamiento pero reimplementando lo
que `Comparator.thenComparing` ya ofrece).

### 3.5 Agregación de intentos como política del reglamento

**Patrón / principio:** Strategy, regla de negocio configurable por versión.

**Dónde:** `domain/ranking/AttemptAggregation`, sus implementaciones en `domain/ranking/aggregation`
(`BestAttempt`, `SumOfAttempts`), `Rulebook.attemptAggregation` y `TeamRuns.aggregatedPoints`
(consultado por `TeamScoreSummary.totalPoints`).

Cuántos de los intentos de un equipo cuentan para su posición es una decisión del reglamento, no del
código: con dos intentos de 30 y uno de 50, la suma pone primero al equipo constante y el mejor
intento al de 50. `AttemptAggregation.aggregate` recibe las corridas puntuadas del equipo y devuelve
el total que ordena el ranking. `BestAttempt` toma el mayor total individual; `SumOfAttempts` los
suma. Ambas devuelven cero si el equipo no tiene corridas.

El reglamento exige una política (el constructor rechaza `null`), el `RulebookDraft` la recibe junto
con los desafíos y desempates, y `GenerateStandingsUseCase` / `RecalculateStandingsUseCase` le pasan
el reglamento a `CategoryScoringService.rank`, que llama a `collect` con su `attemptAggregation()` y
arma el `TeamRuns` de cada equipo: la colección de corridas puntuadas es el lugar donde vive la
política (ver 4.10). Como el reglamento está versionado, una tabla se genera y se recalcula con la
política de su versión (ver 3.3), aunque después se publique otra. El demo y los fixtures de test
publican `BestAttempt`.

Los desempates no cambian: `HighestSingleRunTiebreak` y `FewestPenaltiesTiebreak` siguen mirando
todas las corridas del equipo, también las que la agregación no cuenta; un reglamento que quiera
otra cosa agrega su propia `TiebreakRule`.

**Por qué:** documentar la suma como decisión (versión anterior de 4.8) no la hacía correcta para un
reglamento que exige el mejor intento. Al ser una política versionada, ambas reglas conviven y se
recalculan de forma determinista.

**Alternativas descartadas:** un flag booleano `bestOnly` en el reglamento (no escala a una tercera
política, como promedio o los dos mejores); aplicar la política en `RankingService` (el servicio
tendría que recibir el reglamento completo, cuando sólo necesita el total del equipo); elegir el mejor
intento en `CategoryScoringService` descartando corridas (el resumen perdería los intentos que los
desempates sí consultan).

## 4. Elegibilidad, agenda y resultados

### 4.1 Elegibilidad con Specification y acumulación de violaciones

**Patrón / principio:** Specification, OCP.

**Dónde:** `domain/eligibility/EligibilityRule`, `EligibilityRequirements` y las reglas `AgeRangeRule`,
`TeamCompositionRule`, `RobotClassRule`, `RobotSpecificationRule`, `RequiredDocumentsRule`.

Cada restricción es una `EligibilityRule` que devuelve la lista de violaciones que encuentra.
`EligibilityRequirements` es el conjunto de reglas que exige un reglamento: `verdictFor` aplica todas,
concatena sus violaciones y las envuelve en un `EligibilityVerdict`. No se corta en la primera
violación.

**`EligibilityRequirements` no es una regla.** Antes se llamaba `EligibilityPolicy` y a la vez
implementaba `EligibilityRule` y contenía una lista de `EligibilityRule`. Eso era confuso por dos
motivos: un objeto que es una regla y también la lista de reglas mezcla dos niveles, y *policy* evoca
el patrón Policy/Strategy, cuyo rol acá lo cumplen las reglas intercambiables, no su conjunto. Ahora el
único contrato polimórfico es `EligibilityRule` (cada restricción es una estrategia), y
`EligibilityRequirements` es un value object del reglamento que sólo ofrece `verdictFor`. Nadie
necesitaba anidar conjuntos de requisitos, así que se eliminó la composición recursiva en vez de
inventarle una interfaz propia sin cliente.

**Por qué:** un equipo debe recibir de una sola vez todo lo que tiene que corregir. Además, las
restricciones varían por edición y categoría, así que se configuran en el reglamento en lugar de
estar cableadas en el caso de uso. La categoría aporta edades y clase de robot; el registro usa como
fecha de referencia el inicio de la competencia.

**La decisión la toma el agregado.** `TeamRegistration.resolveWith(EligibilityVerdict)` devuelve una
inscripción nueva aceptada si el veredicto es elegible y rechazada en caso contrario; ya no existen
`accept()` ni `reject(...)` públicos, así que una inscripción inelegible no puede quedar `ACCEPTED` y
`RegisterTeamUseCase` no tiene un `if` sobre el veredicto. Resolver dos veces lanza
`ConflictException`.

**Competir también lo decide la inscripción.** `TeamRegistration.requireAcceptedIn(competitionId,
categoryId)` lanza `RuleViolationException` si el equipo no fue aceptado o si no compite en esa
competencia y categoría. Antes era `ScheduleRoundUseCase.requireEligibleTeam`; ahora la invoca
`Round.schedule` (ver 4.2).

Cada violación lleva un `EligibilityRuleCode` (value object, como `ScoringRuleCode`) y el agregado
conserva las `EligibilityViolation` tipadas en `rejectionReasons()`: se sabe qué regla falló sin
parsear texto. `EligibilityVerdict.reasons()` sólo arma el texto para la auditoría.

**Alternativas descartadas:** validaciones con corte temprano (`if` encadenados que abortan en el
primer error), que obligan a registrarse varias veces para descubrir todos los problemas; validación
por anotaciones estáticas sobre el registro, que por sí solas no expresan el contexto de categoría
y fecha de referencia. Validadores personalizados podrían incorporarlo, pero aquí se eligieron
reglas de dominio explícitas y componibles.

### 4.2 Detección de conflictos de agenda como servicio de dominio

**Patrón / principio:** Domain Service.

**Dónde:** `domain/schedule/ScheduleConflictDetector` y `CompetitionSchedule`, usados por
`ScheduleRoundUseCase`.

Detectar que una pista, un equipo o un juez ya están ocupados requiere mirar turnos de varias rondas,
así que la regla no pertenece a ninguna entidad. `CompetitionSchedule` reúne las rondas de la
competencia y ofrece sus turnos reservados (`bookedHeats()`); el servicio decide con
`requireNoConflicts(booked, candidate)`, que lanza `ScheduleConflictException` (una
`ConflictException`, ver 4.9) con la lista de `ScheduleConflict` detectados. Antes ese `if` sobre la
lista vacía estaba en el caso de uso.

`ScheduleRoundUseCase` incorpora también los candidatos ya aceptados dentro del comando. No busca
reservas en otras competencias. `TimeSlot.overlaps` permite que un turno empiece exactamente cuando
termina otro.

**El número de ronda es único en la categoría.** `CompetitionSchedule.requireAvailableOrdinal
(categoryId, ordinal)` lanza `ConflictException` si la categoría ya tiene una ronda con ese ordinal;
otra categoría puede repetirlo.

**`Round` decide quién puede tener un turno.** `Round.schedule(heat, team)` recibe la
`TeamRegistration` del equipo y exige que el turno sea de esa ronda y de ese equipo, y que
`team.requireAcceptedIn(competitionId, categoryId)` se cumpla (ver 4.1). Así la regla "equipo aceptado
y de la categoría", que antes era `ScheduleRoundUseCase.requireEligibleTeam`, no se puede saltear: no
hay forma de agregar un turno sin presentar la inscripción. `Round` también conserva la invariante de
un turno por equipo (`Heats`) y `heatFor(teamId)` lanza `RuleViolationException` si el equipo no tiene
turno en la ronda (antes lo decidía `CaptureRunResultUseCase` sobre un `Optional`).

**Por qué:** mantiene las reglas en el dominio y testeables sin repositorios, sin forzarlas dentro de
una entidad que no tiene toda la información.

Que un turno caiga dentro de las fechas de la competencia es una invariante distinta y vive donde
están esas fechas: `Competition.requireSlotWithinPeriod(slot)` verifica inicio y fin del turno con
`requireDateWithinPeriod`. Un tipo de conflicto es un `ScheduleConflictType`, no un `String`.

**Alternativas descartadas:** poner la detección de conflictos en el caso de uso (mezcla orquestación
con negocio y dificulta reutilizar la regla de forma aislada); ponerla en `Round` (no ve los turnos
de las demás rondas); dejar el período de la competencia como dato decorativo, lo que permitiría
agendar turnos en fechas ajenas al evento.

### 4.3 Auditoría: el resultado conserva el original y todas las modificaciones

**Patrón / principio:** historial de correcciones dentro del agregado.

**Dónde:** `domain/result/RunResult`, `CorrectionHistory`, `ResultCorrection` y `RoundResults`.

`RunResult` guarda las mediciones e incidentes originales y un `CorrectionHistory`; las mediciones
vigentes son las de la última corrección del historial. El historial exige orden cronológico: una
corrección no puede ser anterior a la previa. Cada corrección registra momento, responsable (`Actor`),
motivo y la apelación que la originó (`AppealId sourceAppeal`, obligatorio: hoy toda corrección nace
de una apelación aceptada, así que no hace falta un `Optional` como campo). Una corrección no puede
ser anterior a la captura. Las evaluaciones de jueces se conservan como fueron capturadas: el modelo
de corrección actual modifica mediciones e incidentes, no esas evaluaciones.

**El agregado valida la captura y la corrección contra su desafío.**

- `RunResult.capture(id, round, teamId, challenge, attempt, at, measurements, evaluations,
  incidents)` es la fábrica de una corrida: obtiene el turno con `round.heatFor(teamId)`, exige que el
  `ChallengeSpec` sea el de la ronda y valida límite de intentos, mediciones, incidentes y evaluaciones
  (`validateEvaluations(evaluations, heat.judges())`) antes de construirla, fijando ronda, turno y
  versión de reglamento. Antes esa secuencia de validaciones vivía en `CaptureRunResultUseCase`.
- `RunResult.applyCorrection(correction, challenge)` rechaza el `ChallengeSpec` de otro desafío y
  valida las mediciones e incidentes corregidos antes de devolver una corrida nueva con la corrección
  en su historial (ver 4.11). Así no se puede corregir una corrida con datos que su propio desafío
  rechazaría, aunque se llame al método por fuera de `AcceptAppealUseCase`.

El constructor público de `RunResult` crea una corrida sin correcciones a partir de datos ya
validados: es el que usaría un adaptador de persistencia para reconstituirla. El flujo de negocio
captura siempre con `RunResult.capture`.

**Un intento se captura una sola vez.** `RoundResults`, la colección de corridas de una ronda,
decide con `requireUnusedAttempt(teamId, attempt)` y lanza `ConflictException` si ese equipo ya tiene
ese intento. Antes era `CaptureRunResultUseCase.requireUnusedAttempt`. Como esa verificación consulta
el repositorio y no alcanza ante dos capturas simultáneas, el contrato de `RunResultRepository` suma el
equivalente de una restricción única de persistencia (ver 4.12).

**Por qué:** cumple "conservar los valores originales y todas las modificaciones" dentro del modelo,
no en una bitácora externa que podría desincronizarse. El recálculo usa siempre los valores vigentes
y la investigación puede reconstruir el camino completo.

**Alternativas descartadas:** actualizar las mediciones en el lugar y anotar el cambio en un log
(pierde la trazabilidad dentro del agregado); event sourcing completo del resultado (ver 5.1).

### 4.4 Bitácora de auditoría como puerto

**Patrón / principio:** DIP, bitácora de auditoría mediante un puerto explícito.

**Dónde:** `domain/audit/AuditLog`, `domain/audit/AuditEvent` y `AuditAction`.

Cada caso de uso que modifica estado registra un `AuditEvent` tipado: la acción es un `AuditAction`,
el sujeto es el `Identifier` del elemento afectado (`RunId`, `TeamId`, `CategoryId`…), el responsable
es un `Actor` y los detalles son un `Map<AuditDetail, String>` con claves de un enum. Como el sujeto
es un id tipado, `AuditLog.findBySubject(Identifier)` no confunde una corrida con una categoría que
tenga el mismo texto. Los valores de detalle se generan desde el dominio: `MeasurementSet.toString()`
describe las mediciones ordenadas por clave (el `toString` de un `Map.copyOf` no tiene orden
definido). Los tests verifican, por ejemplo, que aceptar una apelación deja `APPEAL_RESOLVED` sobre
la apelación, `RESULT_CORRECTED` sobre la corrida y, si la categoría ya tenía posiciones,
`STANDINGS_RECALCULATED` sobre la categoría; rechazarla sólo registra `APPEAL_RESOLVED`.

**Por qué:** la auditoría atraviesa todos los casos de uso y debe poder apuntar mañana a un archivo o
a una base sin tocar el negocio.

**Alternativas descartadas:** eventos de dominio publicados por las entidades con un bus (potente pero
prematuro sin infraestructura asincrónica); registrar la auditoría en el adaptador de persistencia
(perdería el motivo y el responsable de la acción).

### 4.5 Apelaciones como agregado con transiciones protegidas

**Patrón / principio:** máquina de estados en la entidad, encapsulamiento de invariantes.

**Dónde:** `domain/appeal/Appeal`, `AppealWindow`, `Appeals`, `SubmitAppealUseCase`,
`AcceptAppealUseCase` y `RejectAppealUseCase`.

**Presentar.** `Appeal.file(id, run, teamId, claim, submittedAt, window)` es la fábrica de una
apelación y decide las reglas de presentación:

- el equipo sólo apela una corrida propia (`RuleViolationException`; antes era un `if` en
  `SubmitAppealUseCase`);
- el plazo: `AppealWindow` (una `Duration` positiva configurada en el reglamento) exige que la
  apelación llegue a más tardar `length` después de la captura (`requireOpen(capturedAt, submittedAt)`,
  `RuleViolationException`). Se usa el plazo del reglamento fijado en la corrida, no el vigente.

`Appeals`, la colección de apelaciones ya presentadas, rechaza con `ConflictException` una segunda
apelación sobre la misma corrida (`requireNoneOn(runId)`), aunque la primera ya esté resuelta: una
corrida se apela una sola vez. El caso de uso la arma con `AppealRepository.findByRun`.

**Resolver: dos casos de uso en lugar de un flag.** `ResolveAppeal` (con `boolean accepted` y un
`Optional<Correction>`) se reemplazó por:

- `RejectAppeal(appealId, rationale, reviewer)`: rechaza, guarda y audita. No tiene corrección que
  descartar en silencio.
- `AcceptAppeal(appealId, rationale, correctedMeasurements, correctedIncidents, reviewer)`: la
  corrección es obligatoria, así que una apelación no puede quedar `ACCEPTED` sin efecto sobre el
  resultado.

**`AcceptAppealUseCase` sólo coordina.** Las responsabilidades que antes concentraba quedan en el
dominio: `Appeal.accept` resuelve (una sola vez, nunca antes de la presentación),
`RunResult.applyCorrection` valida y aplica la corrección (ver 4.3) y el recálculo es el del puerto
`RecalculateStandings`. El caso de uso carga la apelación, la corrida, la ronda y el reglamento de la
corrida, obtiene la apelación aceptada y la corrida corregida **en memoria** (son inmutables, ver
4.11), y recién entonces persiste ambas y audita. Si la corrección es inválida o la apelación ya
estaba resuelta, la excepción ocurre antes de cualquier `save`: no puede quedar la apelación aceptada
y la corrida sin corregir. Ya no hace falta la validación anticipada que duplicaba la del agregado.

**Aceptar dispara el recálculo.** Si la categoría de la corrida ya tiene posiciones,
`AcceptAppealUseCase` invoca `RecalculateStandings` (inyectado desde el composition root) con el
motivo "appeal … accepted": quien acepta no tiene que acordarse de recalcular. Si todavía no se
generaron posiciones, no hay nada que recalcular; la generación posterior ya usa los valores
corregidos. Reusar el puerto de entrada evita duplicar el recálculo y su auditoría.

**Publicar exige apelaciones resueltas** (`Appeals.requireNonePending`, ver 3.3).

**Por qué:** las reglas de presentación y de transición viven en el dominio, no en los casos de uso,
que sólo orquestan; cada caso de uso responde a una sola decisión del jurado. `TeamRegistration`
protege sus transiciones con el mismo criterio que `Appeal`: una inscripción se decide una sola vez.

Esto no equivale a una transacción: la persistencia, la auditoría o el recálculo posterior todavía
pueden fallar después de guardar la apelación aceptada (ver sección 8).

**Alternativas descartadas:** un campo de estado editable desde afuera (cualquier código podría dejar
la apelación en un estado inconsistente); un único caso de uso con un flag booleano (dos
comportamientos con contratos distintos detrás de un `if`); mutar primero y validar después confiando
en que la ausencia de `save()` alcanza para descartar el cambio, que sólo es cierto mientras el
adaptador no devuelva la instancia viva que acaba de mutarse; publicar un evento de dominio para el
recálculo (ver 5.3).

### 4.6 Value objects tipados en lugar de primitivos

**Patrón / principio:** Value Object, evitar Primitive Obsession.

**Dónde:** `domain/shared` (`Points`, `DateRange`, `AgeRange`, `Actor`, e identificadores como
`TeamId`, `RunId`, `MemberId`), `domain/challenge` (`MetricKey`, `MetricValue`, `MetricUnit`,
`MeasurementSet`, `AttemptNumber`, `AttemptLimit`), `domain/scoring` (`JudgeScore`, `PointsRate`,
`PointsAmount`), `domain/schedule/RoundOrdinal`, `domain/ranking/Revision`, `domain/team/Weight` y
`domain/eligibility/EligibilityRuleCode`.

Los identificadores son records distintos que implementan `Identifier`, de modo que pasar un
`CategoryId` donde se espera un `TeamId` no compila. `Points` normaliza la escala decimal y `MetricValue`
rechaza valores negativos; `MetricKind` valida que un conteo de objetivos sea entero y que una razón
de precisión no supere 1.

**Comportamiento por constante, no `switch`.** `MetricKind.accepts` es un método abstracto que cada
constante implementa (todas rechazan negativos con el helper privado `isNonNegative`; `OBJECTIVE_COUNT`
exige además un entero y `PRECISION_RATIO` un valor ≤ 1). Lo mismo `ThresholdBonusRule.Comparison`:
`AT_LEAST` y `AT_MOST` implementan `isMetBy(measured, threshold)`. Antes ambos hacían `switch (this)`
o `switch (comparison)`; una constante nueva obligaba a editar ese `switch` y el `default` ocultaba
el olvido. Ahora el compilador exige que la constante nueva declare su propio criterio (OCP).

Ningún concepto del dominio viaja como primitivo suelto:

| Antes | Ahora | Invariante que aporta |
| --- | --- | --- |
| `String actor`, `reviewer` (comandos, `AuditEvent`, `ResultCorrection`, `AppealDecision`) | `Actor` | no vacío, normalizado |
| `String subject` en `AuditEvent` / `AuditLog.findBySubject` | `Identifier` | el sujeto es un id tipado |
| `Map<String, String> details` | `Map<AuditDetail, String>` | claves de un enum, no texto libre |
| `String ruleCode` en `EligibilityViolation` | `EligibilityRuleCode` | igual que `ScoringRuleCode` |
| `List<String> rejectionReasons` | `List<EligibilityViolation>` | el agregado conserva qué regla falló |
| `int attemptNumber` / `int maximumAttempts` | `AttemptNumber` / `AttemptLimit` | positivos; `AttemptLimit.allows(attempt)` |
| `int ordinal` (ronda) / `int revision` (posiciones) | `RoundOrdinal` / `Revision` | positivos; `Revision.next()` |
| `BigDecimal weightKg` | `Weight` | positivo, `exceeds(limit)` |
| `String unit` + `boolean required` | `MetricUnit` + `MetricRequirement` | unidad no vacía; requerimiento con nombre |
| `Optional<AppealId> sourceAppeal` en `ResultCorrection` | `AppealId` | toda corrección nace de una apelación |
| `Optional<RulebookVersion>` en `Competition` y `FindCompetition.View` | `RulebookVersion` | la competencia nace con su reglamento (3.1) |
| `boolean accepted` + `Optional<Correction>` en `ResolveAppeal.Command` | `AcceptAppeal` / `RejectAppeal` | la aceptación exige corrección (4.5) |
| `Duration` suelta para el plazo de apelación | `AppealWindow` | positiva; `requireOpen(capturedAt, submittedAt)` |

`Optional` se usa sólo como tipo de retorno (por ejemplo `findById` o `Appeal.decision()`), nunca como
campo ni como parámetro.

**`Points` ya no representa conceptos distintos.** `Points` es el puntaje (con signo) de una
contribución o un total. La nota de un juez es un `JudgeScore` con escala 0–10; un coeficiente de
configuración es un `PointsRate` y un monto fijo configurado (tope, bono, deducción) es un
`PointsAmount`; ninguno de estos dos admite negativos (ver 2.1). Comparten la aritmética porque
`PointsRate.times` y `PointsAmount.times` producen `Points`, pero no las reglas.

**`Member` tiene identidad.** `Member` lleva un `MemberId`: dos integrantes homónimos nacidos el mismo
día son integrantes distintos. `RegisterTeam.Command` recibe `MemberDraft` (nombre, nacimiento, rol)
y `RegisterTeamUseCase` asigna los ids con `IdGenerator.nextMemberId()`, igual que
`CreateCompetition` hace con `CategoryDraft`.

**Por qué:** las invariantes intrínsecas del valor se concentran en su construcción. Las que dependen
del contexto se verifican donde corresponde: por ejemplo, `MetricKind.accepts` se consulta desde
`MetricDefinition.validate`, invocado por `ChallengeSpec.validate`. Con `String` o `BigDecimal`
sueltos se pierde distinción semántica y las validaciones tienden a dispersarse.

Los doce identificadores repiten la validación y la fábrica `of`. **Es duplicación deliberada.**
Los records nominales hacen explícitos los tipos `CategoryId` y `TeamId` en las firmas y evitan
confundirlos. Un `Id<T>` correctamente diseñado también podría preservar esa distinción; se
prefirieron tipos concretos por su legibilidad y simplicidad en este módulo.

`ScoringRuleCode` es otro value object nominal. `ScheduleConflictType`, en cambio, es un enum que
restringe los tipos de conflicto a `ARENA_BUSY`, `TEAM_BUSY` y `JUDGE_BUSY`. Ambos reemplazan textos
libres por tipos explícitos; no implican el mismo mecanismo de validación.

**Alternativas descartadas:** `UUID`/`String` para todos los ids (intercambiables por error); `double`
para puntajes (errores de representación binaria); usar un único tipo común de identificador en
todas las firmas para ahorrar repetición, perdiendo la distinción nominal.

### 4.7 Tiempo e identificadores inyectados

**Patrón / principio:** DIP aplicado a dependencias ambientales.

**Dónde:** `java.time.Clock` e `IdGenerator` inyectados en los casos de uso;
`infrastructure/id/SequentialIdGenerator`.

`IdGenerator` declara nueve métodos para los identificadores creados por los casos de uso: temporada,
competencia, categoría, equipo, integrante, ronda, turno, corrida y apelación. No genera `ChallengeId`, `ArenaId`
ni `JudgeId`, que llegan desde la configuración o las entradas. El formato y los contadores viven
en `SequentialIdGenerator`; un `nextId(String prefix)` genérico repartiría esos detalles entre los
casos de uso y permitiría confundir prefijos sin ayuda del compilador.

**Por qué:** ningún caso de uso llama a `Instant.now()` ni genera ids por su cuenta, así que los tests
corren con un reloj fijo y con identificadores predecibles, y las aserciones sobre marcas de tiempo
no dependen del momento de ejecución.

**Alternativas descartadas:** `Instant.now()` y `UUID.randomUUID()` directos, que vuelven los tests no
determinísticos.

### 4.8 Reutilización del cálculo entre generar y recalcular

**Patrón / principio:** DRY, servicio de dominio.

**Dónde:** `domain/ranking/CategoryScoringService`.

Cómo se puntúa una corrida y cómo se arma la tabla de una categoría es lógica de negocio, así que el
servicio vive en el dominio. Antes estaba en `application/service` con el argumento de que en el
dominio "lo obligaría a conocer repositorios"; ese argumento dejó de valer cuando los repositorios
pasaron a ser interfaces del dominio (ver 1.3): el servicio depende de `RoundRepository`,
`RunResultRepository` y `RulebookRepository` sin conocer ninguna implementación.

Recorre las rondas de una categoría (`runsOf`), puntúa cada corrida con su reglamento fijado
(`scoreRun`), arma los `TeamScoreSummary` (`collect`) y los ordena con `RankingService` según la
agregación y los desempates de un reglamento (`rank`). `GenerateStandingsUseCase` y
`RecalculateStandingsUseCase` usan `rank`; `CalculateRunScoreUseCase` reutiliza `scoreRun` para una
corrida individual y `PublishStandingsUseCase` usa `runsOf` para reunir las apelaciones de la
categoría. `collect` recibe la `AttemptAggregation` del reglamento y arma con ella el
`TeamRuns` de cada `TeamScoreSummary`, que reúne todos los intentos capturados de todas las rondas de
la categoría; `TeamRuns.aggregatedPoints` delega en la política para decidir cuáles cuentan (ver 3.5). Un equipo sin corridas
capturadas no aparece en la colección ni en el ranking generado.

**Por qué:** si cada caso de uso armara la tabla por su cuenta, generar y recalcular podrían divergir,
que es exactamente el error que el requisito de recálculo busca evitar.

**Alternativas descartadas:** duplicar el recorrido en cada caso de uso; dejarlo como servicio de
aplicación, que dejaba lógica de puntaje fuera del dominio; un servicio puro que reciba corridas y
reglamentos ya cargados (también válido, pero obligaba a cada caso de uso a repetir la carga de rondas,
corridas y reglamentos por versión).

### 4.9 Jerarquía de excepciones del dominio

**Patrón / principio:** excepciones por categoría de falla, Open/Closed.

**Dónde:** `domain/shared/DomainException` (abstracta) y sus subclases `InvalidValueException`,
`RuleViolationException`, `ConflictException` y `NotFoundException`; `ScheduleConflictException`
extiende `ConflictException`.

| Excepción | Significa | Ejemplos |
| --- | --- | --- |
| `InvalidValueException` | un valor o una configuración no cumple su invariante | id vacío, puntaje de juez fuera de 0–10, métrica declarada dos veces, `PointsRate` negativo |
| `RuleViolationException` | una operación con datos bien formados viola una regla de negocio | intento fuera del límite, medición que el desafío no define, juez fuera del heat, turno fuera del período, equipo no aceptado o de otra categoría, apelación fuera de plazo o sobre una corrida ajena |
| `ConflictException` | la operación choca con el estado actual | apelación o inscripción ya resueltas, posiciones ya definitivas o ya generadas, intento ya capturado, conflicto de agenda, ordinal de ronda repetido, temporada superpuesta, corrida ya apelada, apelaciones pendientes al publicar, captura en una categoría con posiciones definitivas |
| `NotFoundException` | el elemento referenciado no existe | competencia, ronda o corrida inexistentes; categoría que no pertenece a la competencia; desafío que no está en el reglamento |

`DomainException` es abstracta: no se puede lanzar sin elegir la categoría. `NotFoundException` dejó de
vivir en `application` y de extender directamente `RuntimeException`; ahora es parte de la misma
jerarquía y la usan tanto el dominio como los casos de uso. `ScheduleConflictException` expone la
lista de `ScheduleConflict`, así quien la atrapa no necesita parsear el mensaje.

**Por qué:** con una única excepción no se podía distinguir qué falló sin leer el texto. Las cuatro
categorías son las que un adaptador HTTP de la Entrega 2 necesitará mapear (400/422, 422, 409 y 404)
sin conocer cada regla.

**Alternativas descartadas:** una excepción por regla (cientos de clases sin un cliente que las
distinga); un código de error en una única excepción (vuelve a obligar a comparar valores para
decidir el tratamiento).

### 4.10 Colecciones con nombre propio

**Patrón / principio:** First-Class Collection, lenguaje ubicuo.

**Dónde:** `domain/scoring/JudgeEvaluations`, `domain/ranking/TeamRuns`, `domain/result/CorrectionHistory`,
`domain/schedule/Heats`, `domain/team/TeamMembers` y las colecciones que concentran reglas que antes
decidían los casos de uso: `domain/competition/SeasonCalendar`, `domain/schedule/CompetitionSchedule`,
`domain/result/RoundResults`, `domain/ranking/StandingsHistory` y `domain/appeal/Appeals`.

| Colección | Reemplaza a | Invariante que concentra |
| --- | --- | --- |
| `JudgeEvaluations` | `List<JudgeEvaluation>` | un juez evalúa una vez cada criterio; `requireEvaluatorsWithin(panel)` |
| `TeamRuns` | `List<ScoredRun>` + agregación en `TeamScoreSummary` | una corrida cuenta una sola vez; es donde se aplica la `AttemptAggregation` |
| `CorrectionHistory` | `List<ResultCorrection>` | orden cronológico; la última corrección es la vigente |
| `Heats` | `List<Heat>` en `Round` | un equipo tiene un único turno por ronda |
| `TeamMembers` | `List<Member>` | al menos un integrante, sin integrantes repetidos; `competitors()`, `hasCoach()` |
| `SeasonCalendar` | consulta suelta en `CreateSeasonUseCase` | dos temporadas no se superponen; `requireAvailable(period)` |
| `CompetitionSchedule` | recorrido de rondas en `ScheduleRoundUseCase` | ordinal único por categoría; `bookedHeats()` para detectar conflictos |
| `RoundResults` | `CaptureRunResultUseCase.requireUnusedAttempt` | un intento de un equipo se captura una sola vez |
| `StandingsHistory` | `findLatest(...).isPresent()` en `GenerateStandingsUseCase` | se genera una sola vez; tras una revisión `FINAL` no se capturan corridas |
| `Appeals` | ningún control | una corrida se apela una sola vez; no se publica con apelaciones pendientes |

**Evaluaciones de jueces.** Antes un mismo juez podía evaluar dos veces el mismo criterio (con
`J1=10, J1=10, J2=0` el promedio daba 6,67 en lugar de 5) y no se verificaba que perteneciera al
heat. Ahora `JudgeEvaluations` rechaza la repetición al construirse y
`ChallengeSpec.validateEvaluations(evaluations, heat.judges())` verifica que cada criterio sea una
métrica `JUDGE_CRITERION` del desafío y que cada juez esté asignado al heat. `RunResult.capture` la
invoca junto con las demás validaciones del desafío (ver 4.3).

**Por qué:** además de aportar lenguaje ubicuo, cada colección es el único lugar donde se puede
romper su invariante, en vez de repetir la verificación en cada consumidor.

### 4.11 Agregados inmutables

**Patrón / principio:** Value Object / entidades inmutables, Tell Don't Ask.

**Dónde:** `Competition`, `TeamRegistration`, `Round`, `RunResult` y `Appeal` (además de `Standings`,
que ya lo era).

Todos los campos de los agregados son `final`. Las transiciones no modifican la instancia: devuelven
una nueva, construida con un constructor privado que recibe el estado completo.

| Agregado | Transición | Devuelve |
| --- | --- | --- |
| `Competition` | `activateRulebook(version)` | competencia con otra versión activa |
| `TeamRegistration` | `resolveWith(verdict)` | inscripción aceptada o rechazada |
| `Round` | `schedule(heat, team)` | ronda con un turno más |
| `RunResult` | `applyCorrection(correction, challenge)` | corrida con la corrección en su historial |
| `Appeal` | `accept(decision)` / `reject(decision)` | apelación resuelta |

El constructor público de cada uno crea sólo el estado inicial (inscripción `SUBMITTED`, ronda sin
turnos, corrida sin correcciones, apelación `SUBMITTED`); para llegar a otro estado hay que pasar por
la transición que lo valida.

**Por qué:**

- **Persistir al final.** Un caso de uso puede obtener todas las instancias nuevas, dejando que el
  dominio valide cada paso, y guardarlas recién al final. Si algo falla, no quedó nada a medio
  mutar, ni siquiera con un adaptador en memoria que guarda la instancia viva (ver 4.5).
- **Consultas seguras.** Una consulta que devuelve el agregado no habilita a modificarlo por fuera
  del caso de uso y la auditoría (ver 1.2).
- **Completo desde la creación.** La competencia nace con su reglamento (3.1) y ninguna transición
  deja un agregado en un estado intermedio.

**Alternativas descartadas:** mantener los mutadores y reordenar los pasos del caso de uso (dependía de
recordar qué método valida antes de mutar); devolver vistas en todas las consultas sin resolver la
mutabilidad del modelo.

### 4.12 Reglas que dependen de otros agregados o del repositorio

**Patrón / principio:** reglas en el dominio, casos de uso como coordinadores; restricción de
persistencia como red de seguridad.

**Dónde:** las colecciones de 4.10, `Season.requireCompetitionPeriodInside`,
`Competition.requireSlotWithinPeriod`, `TeamRegistration.requireAcceptedIn`, `RunResult.capture`,
`Appeal.file` y el contrato de `RunResultRepository`.

Varias reglas necesitan datos que el agregado no tiene: la unicidad de un intento, de un ordinal o de
una temporada, el cierre de una categoría o las apelaciones pendientes. Antes cada una era un `if`
en el caso de uso, sobre el resultado de una consulta. Ahora el caso de uso **sólo carga** los datos
(un repositorio, otro agregado) y el **dominio decide** con un método que dice lo que exige
(`requireUnusedAttempt`, `requireAvailableOrdinal`, `requireOpenForResults`, `requireNonePending`…).
Es el mismo criterio que ya seguía `Season.requireCompetitionPeriodInside` en `CreateCompetition`.

| Regla | Antes | Ahora |
| --- | --- | --- |
| El intento no se capturó antes | `CaptureRunResultUseCase.requireUnusedAttempt` | `RoundResults.requireUnusedAttempt` + restricción del repositorio |
| Equipo aceptado y de la categoría | `ScheduleRoundUseCase.requireEligibleTeam` | `TeamRegistration.requireAcceptedIn`, invocada por `Round.schedule` |
| Veredicto → aceptar/rechazar | `RegisterTeamUseCase` | `TeamRegistration.resolveWith` |
| No generar dos veces la misma tabla | `GenerateStandingsUseCase` | `StandingsHistory.generate` |
| Ordinal de ronda único en la categoría | (no existía) | `CompetitionSchedule.requireAvailableOrdinal` |
| No capturar con posiciones `FINAL` | (no existía) | `StandingsHistory.requireOpenForResults` |
| Temporadas sin superposición | (no existía) | `SeasonCalendar.requireAvailable` |
| Apelar una corrida propia, en plazo, una sola vez | `if` en `SubmitAppealUseCase` (plazo y duplicados no existían) | `Appeal.file` + `AppealWindow` + `Appeals.requireNoneOn` |
| Publicar sin apelaciones pendientes | (no existía) | `Appeals.requireNonePending` |
| Equipo con turno en la ronda; validar la captura | `CaptureRunResultUseCase` | `Round.heatFor`, `RunResult.capture` |
| Turno dentro del período (inicio y fin) | `ScheduleRoundUseCase.requireSlotWithinCompetition` | `Competition.requireSlotWithinPeriod` |
| Hay conflictos → rechazar | `if` en `ScheduleRoundUseCase` | `ScheduleConflictDetector.requireNoConflicts` |

**Concurrencia.** Verificar consultando el repositorio no escala a dos pedidos simultáneos: ambos
pueden ver que el intento está libre y guardar. Por eso, además de la regla del dominio, el contrato
de `RunResultRepository.save` rechaza con `ConflictException` una corrida distinta para la misma
ronda, equipo e intento, el equivalente de una restricción única en una base de datos.
`InMemoryRunResultRepository` lo implementa con métodos `synchronized`, y
`RunResultRepositoryContractTest` fija el comportamiento para cualquier adaptador. El ordinal de
ronda, la superposición de temporadas, la generación única y las apelaciones duplicadas tienen la
misma limitación; una persistencia real debería sumar restricciones equivalentes (ver sección 8).

**Por qué:** los casos de uso coordinan y no deciden; las reglas quedan en el dominio, testeables sin
repositorios, y los agregados dejan de ser anémicos en los bordes.

**Alternativas descartadas:** dejar la unicidad sólo en el caso de uso (no explica la regla en el
lenguaje del dominio y no protege ante concurrencia); dejarla sólo en la persistencia (la regla se
vuelve un detalle de cada adaptador); ampliar los agregados para que contengan a los otros (por
ejemplo, que `Round` guarde las corridas), que mezclaría ciclos de vida distintos.

## 5. Patrones que decidimos no aplicar

### 5.1 Event Sourcing

No se aplicó: el estado se guarda como agregados, no como secuencia de eventos. La trazabilidad que
exige la consigna se cubre con el historial de correcciones de `RunResult`, las revisiones de
`Standings` y la bitácora `AuditLog`.

**Consecuencia:** no existe una reconstrucción general del estado del sistema a cualquier instante
arbitrario a partir de eventos. Sí se conservan originales, correcciones, decisiones y revisiones
para los flujos implementados. El recálculo de puntajes existe sin Event Sourcing: usa valores vigentes
y las versiones de reglas fijadas. Una reconstrucción temporal más amplia requeriría otro diseño.

### 5.2 CQRS

No se adoptaron modelos de lectura y escritura independientes. Los casos de uso tienen distintos
puertos de entrada, pero comparten repositorios y modelos de negocio. Proyecciones de salida como
`FindCompetition.View` no constituyen por sí solas una infraestructura de modelos de lectura separados.

**Consecuencia:** consultas de gran volumen (posiciones históricas de todas las categorías) pasarán por
los mismos repositorios y podrán ser menos eficientes. A esta escala, la complejidad de mantener dos
modelos no se justifica.

### 5.3 Eventos de dominio con bus de publicación

No se aplicó: aceptar una apelación no emite un evento. `AcceptAppealUseCase` invoca de forma
sincrónica el puerto `RecalculateStandings` cuando la categoría ya tiene posiciones (ver 4.5), y
`RecalculateStandings` sigue disponible para que el operador lo invoque por otros motivos.

**Consecuencia:** la cadena corrección → recálculo es automática pero está cableada en el composition
root, no desacoplada por un bus: si mañana otra operación también debe recalcular, hay que invocarlo
explícitamente o introducir eventos. Publicar sigue siendo una decisión del operador. A cambio, el
flujo es explícito, sincrónico y fácil de auditar, sin infraestructura de mensajería que esta entrega
no tiene.

### 5.4 Framework de inyección de dependencias

No se aplicó: el cableado es manual en `RoboLeagueCompositionRoot`.

**Consecuencia:** exponer un caso de uso nuevo desde el composition root exige modificar ese archivo.
A cambio, el ensamblado es visible y la producción sólo depende del JDK. Los tests sí usan JUnit
Jupiter, y Maven usa los plugins de compilación, verificación y empaquetado declarados en `pom.xml`.

### 5.5 Framework de mocking

No se usa un framework de mocking. La mayoría de las pruebas de casos de uso usan el composition
root con adaptadores en memoria reales; las pruebas de dominio construyen objetos directamente.
`AppealRecalculationTest.anAppealFromAnotherTeamIsRejectedWithoutSavingOrAuditingIt` instancia el
caso de uso directamente con dobles manuales de `AppealRepository` y `AuditLog` que registran las
escrituras para comprobar que el rechazo no guarda ni audita una apelación. El plazo de apelación se
prueba con `support/AdjustableClock`, un `Clock` de test que avanza a demanda.

**Consecuencia:** un cambio en la firma de un puerto puede exigir actualizar sus adaptadores y dobles
manuales. La suite se centra en resultados, estados y efectos observables; el escenario con dobles
también comprueba la ausencia de escrituras. No depende de expectativas configuradas con una
biblioteca de mocking.

### 5.6 Repositorio genérico y clase base de entidad

No se aplicó: no hay `Repository<T, ID>` ni `AbstractEntity`.

**Consecuencia:** cada puerto declara sus propias consultas, con algo de repetición entre adaptadores
en memoria. A cambio, ninguna entidad hereda comportamiento que no necesita y cada repositorio expone
sólo lo que el negocio usa (ISP). Lo que sí se comparte entre adaptadores es el **test de contrato**,
que es donde la repetición sí sería peligrosa.

### 5.7 Motor de reglas configurable por datos

No se aplicó: las reglas de puntaje se componen en código Java, no se interpretan desde una
configuración externa o un DSL.

**Consecuencia:** incorporar una fórmula inédita requiere una implementación Java y distribuir ese
código, no solamente editar un archivo de configuración. Los coeficientes de las estrategias
existentes sí se pasan por constructor al armar un reglamento nuevo. Las reglas son tipadas,
testeables y depurables; un intérprete propio hubiera agregado una complejidad que esta entrega no
necesita. La interfaz `ScoringRule` deja la puerta abierta a agregar un adaptador que construya reglas
desde datos.

### 5.8 Persistencia real, API REST y seguridad

Fuera del alcance de esta entrega según la consigna. Los adaptadores en memoria existen para poder
ejecutar y probar el dominio de punta a punta.

**Consecuencia:** no hay transaccionalidad ni concurrencia global: un caso de uso que escribe en
varios repositorios no es atómico. Los puertos existentes permiten sustituir el almacenamiento,
pero una integración real también deberá resolver mapeo, transacciones, concurrencia y fallos.

### 5.9 Métodos en las interfaces "por si acaso"

No se agregan operaciones anticipando clientes inexistentes. `ScoringRule` declara sólo `apply` y
`referencedMetrics` (cuyo cliente es la validación de `ChallengeSpec`, ver 2.2); el helper por defecto
`breakdownFor` se eliminó porque sólo lo usaban los tests, que ahora arman `new
ScoreBreakdown(rule.apply(context))`. `EligibilityRule` sólo declara `evaluate`. Por el mismo criterio
desaparecieron `RulebookRepository.findLatest`, `Competition.requireActiveRulebookVersion` y
`ResultCorrection.fromAppeal`, que quedaron sin cliente. Ninguna de las dos interfaces
tiene un método polimórfico `code()`: las implementaciones etiquetan sus contribuciones o violaciones
con constantes propias. En cambio, `TiebreakRule` conserva `code()` y `description()`, utilizados
por `AppliedTiebreak.of` para registrar el criterio discriminante.

**Consecuencia:** identificar o desactivar una regla de puntaje de forma polimórfica no es una
capacidad de su interfaz actual. Si aparece ese requisito, habrá que definir el contrato con un
cliente y una necesidad concretos.

### 5.10 Composite explícito para combinar reglas de puntaje

No existe `CompositeScoringRule` en el código actual. `ChallengeSpec` sostiene directamente una
lista de reglas y combina sus contribuciones con las deducciones de su catálogo mediante
`Stream.concat` (ver 2.2). Un wrapper obligatorio alrededor de esa lista agregaría otro mecanismo
de combinación sin un segundo consumidor que lo justificara.

**Consecuencia:** la configuración actual es plana y no ofrece bloques de puntaje anidados con
nombre propio. Si aparece esa necesidad, se podría incorporar una `ScoringRule` que envuelva una
sublista, sin cambiar la interfaz. El mismo criterio se aplicó a la elegibilidad:
`EligibilityRequirements` contiene reglas pero ya no es una `EligibilityRule` (ver 4.1).

## 6. Cobertura de los requisitos obligatorios

| Capacidad | Dónde se resuelve |
| --- | --- |
| Configuración del evento | `CreateSeasonUseCase`, `CreateCompetitionUseCase`, `Season`, `SeasonCalendar`, `Competition`, `Category`, `RulebookDraft` |
| Registro de equipos | `RegisterTeamUseCase`, `TeamRegistration`, `TeamMembers`, `Member`, `Robot`, `Weight`, `TeamDocument` |
| Elegibilidad | `EligibilityRequirements` y las reglas de `domain/eligibility/rule` |
| Configuración de desafíos | `ChallengeSpec`, `MetricDefinition`, `domain/scoring/rule/*`, `PenaltyDefinition`, `PointsRate`, `PointsAmount` |
| Programación | `ScheduleRoundUseCase`, `Round`, `Heat`, `TimeSlot`, `CompetitionSchedule`, `ScheduleConflictDetector`, `Competition.requireSlotWithinPeriod`, `TeamRegistration.requireAcceptedIn` |
| Captura de resultados | `CaptureRunResultUseCase`, `RunResult.capture`, `RoundResults`, `StandingsHistory.requireOpenForResults`, `MeasurementSet`, `JudgeEvaluations`, `JudgeScore`, `IncidentReport` |
| Cálculo explicable | `CalculateRunScoreUseCase`, `ScoreBreakdown`, `ScoreContribution`, `ContributionKind` |
| Ranking | `CategoryScoringService`, `RankingService`, `TeamRuns`, `AttemptAggregation`, `domain/ranking/aggregation/*`, `TiebreakRule`, `domain/ranking/rule/*` y `AppliedTiebreak` |
| Publicación | `Standings`, `StandingsHistory`, `PublicationStatus`, `GenerateStandingsUseCase`, `PublishStandingsUseCase`, `GetStandingsUseCase` |
| Apelaciones | `Appeal`, `AppealWindow`, `Appeals`, `SubmitAppealUseCase`, `AcceptAppealUseCase`, `RejectAppealUseCase` |
| Recálculo | `RecalculateStandingsUseCase` (también disparado por `AcceptAppealUseCase`), `CategoryScoringService` |
| Auditoría | `CorrectionHistory`, `Standings.revision()`, `AuditLog`, `AuditEvent`, `AuditDetail`, `Actor`, `FindAuditTrailUseCase` |

## 7. Estrategia de pruebas

Los tests unitarios cubren las reglas donde vive el negocio: cálculo de cada criterio de puntaje y su
composición, validación de mediciones contra el desafío, elegibilidad, desempates y posiciones
compartidas, conflictos de agenda, historial de correcciones y transiciones de apelaciones y
publicación. También las reglas que antes decidían los casos de uso y ahora viven en el dominio
(4.12): `StandingsHistoryTest`, `RunResultTest` (captura validada y `RoundResults`), `AppealTest`
(plazo, equipo propio, `Appeals`) y `DomainEdgeCasesTest` (`requireAcceptedIn`, `Round.schedule`,
`CompetitionSchedule`, `SeasonCalendar`, `MetricKind` por constante, transiciones inmutables).

Un test parametrizado recorre las siete reglas simples enumeradas en `ScoringRulesTest.everyRule`
y verifica el contrato común (ver 2.1.1). Una implementación nueva debe incorporarse a ese proveedor
y tener pruebas de su fórmula; no existe descubrimiento automático de clases. La combinación de la
lista de reglas con el catálogo de penalizaciones y la separación de tipos de contribución se
verifican en `ChallengeSpecTest`, no mediante tests de una clase Composite.

La mayoría de las pruebas de casos de uso ejercitan los adaptadores en memoria a través del
composition root, con un reloj fijo e identificadores secuenciales. También existe la prueba con
dobles manuales descrita en 5.5. Se cubren los escenarios de negocio
más relevantes: aceptar y rechazar una inscripción, programar una ronda con conflictos o fuera del
período de la competencia, capturar resultados con validaciones de mediciones y de incidentes,
obtener el desglose explicable, sostener la versión de reglamento fijada al capturar, publicar
posiciones y el circuito completo de apelación aceptada, corrección y recálculo que reordena la
tabla —ahora disparado por la aceptación—, incluido el caso en que la corrección se rechaza y ni la
apelación, ni la corrida, ni las posiciones cambian. Se cubren además el plazo de apelación, la
apelación duplicada, la publicación bloqueada por apelaciones pendientes, la captura rechazada en una
categoría con posiciones definitivas, el ordinal de ronda repetido y las temporadas superpuestas.

`StandingsRepositoryContractTest` y `RunResultRepositoryContractTest` fijan el comportamiento esperado
de esos puertos; `InMemoryStandingsRepositoryTest` e `InMemoryRunResultRepositoryTest` los heredan.
Los adaptadores futuros deberían reutilizar esas suites; no existe un mecanismo que fuerce
automáticamente esa herencia.

`support/TestEdition` configura las pruebas de casos de uso con `support/RescueEditionFixture`,
propio de `src/test`; las pruebas de dominio también construyen configuraciones aisladas para sus
escenarios. La demo usa por separado `DemoRulebook`, de visibilidad de paquete. Las pruebas del
dominio y de casos de uso no dependen de esa configuración. `DemoScenarioTest` sí verifica
intencionalmente el demo: que se ejecute sin excepción y registre generación, publicación y recálculo.
No reemplaza las aserciones de resultados de negocio de los tests específicos.

## 8. Evolución: qué cambia cuando cambia una dependencia o una regla

**Criterio:** identificar cambios plausibles y localizar su impacto. No es posible garantizar que
cualquier requisito futuro se resuelva sin modificar el dominio. Se preserva el núcleo cuando el
cambio es tecnológico o de representación; un cambio semántico se modela donde corresponde.
No se crean interfaces ni integraciones sin un cliente real.

Actualmente no existe una API HTTP ni una API externa consumida. Los puertos de entrada son la API
Java del módulo. Las siguientes fronteras describen cómo integrar esas tecnologías cuando haya un
requisito concreto, no componentes HTTP ya implementados.

| Cambio | Punto de adaptación | Condición y prueba necesaria |
| --- | --- | --- |
| Cambia una URL, un campo JSON o un código HTTP de nuestra futura API | Controller, DTO y mapper de entrada/salida | Conservar el significado de la entrada tipada (`Command` o ID); probar formato y errores en el adaptador |
| Se sustituye REST por otra entrada, como mensajería | Nuevo adaptador que invoca los mismos puertos de entrada | Un transporte asíncrono también exige decidir duplicados, orden e idempotencia |
| Cambia la API de un proveedor externo | Adaptador detrás de un puerto de salida definido por la necesidad de aplicación | Traducir datos, unidades y errores; probar contrato e integración con el proveedor |
| Se reemplaza el proveedor completo | Nueva implementación del mismo puerto y nuevo ensamblado | Sólo es sustituible si conserva el contrato semántico; una capacidad faltante exige una decisión del negocio |
| Memoria se reemplaza por SQL u otro almacenamiento | Adaptadores de repositorio y composition root | Preservar versiones/revisiones, ausencia y orden; reutilizar tests de contrato y agregar integración real; traducir a restricciones únicas las reglas de unicidad de 4.12 |
| Cambia reloj o formato de identificadores | `Clock` o implementación de `IdGenerator` | No usar el texto del ID como desempate de negocio; distinguirlo del orden técnico de empates completos (3.4); usar reloj controlado en pruebas |
| Cambia un coeficiente o umbral | Configuración de una nueva versión del reglamento | Probar resultado esperado y conservación del cálculo anterior |
| Aparece una fórmula nueva | Nueva `ScoringRule`, configuración y pruebas | Conservar contribuciones explicadas, tipos y comportamiento con datos ausentes |
| Aparece una restricción o desempate | Nueva `EligibilityRule` o `TiebreakRule` | Verificar composición, prioridad y contratos |
| Cambia cómo cuentan los intentos (mejor intento, suma, promedio) | Nueva versión del reglamento con otra `AttemptAggregation` | Probar escenarios donde las políticas producen ganadores distintos y la conservación de la tabla anterior |
| Cambia el plazo de apelación | Nueva versión del reglamento con otra `AppealWindow` | Probar el límite exacto y que una corrida conserva el plazo de su versión |
| Cambian permisos o etapas de apelación | `Appeal`, `Appeals` y, si corresponde, estados de dominio y casos de uso | Probar transiciones permitidas y prohibidas; el DTO HTTP no decide estas políticas |

Una API de entrada debería recorrer `HTTP DTO → mapper → entrada tipada → caso de uso` y mapear la
respuesta a un DTO propio. Serializar los agregados como contrato público acoplaría la API
al modelo interno, aunque sean inmutables. La entrada tipada puede ser un `Command` o el ID que recibe una consulta. El mapper
traduce representación; fórmulas, elegibilidad y transiciones quedan
en dominio. Cambiar `elapsed_ms` por `time_seconds` requiere convertir unidades, no sólo renombrar.

Para un proveedor externo de captura, un futuro puerto podría expresar «obtener las mediciones de
una corrida» usando tipos del módulo. URL, autenticación, SDK y respuestas del proveedor vivirían
en el adaptador. Ese puerto todavía no existe y se definirá según una necesidad concreta. Si el
proveedor nuevo sólo entrega un puntaje final y el negocio exige mediciones para explicar y
recalcular, ningún mapper puede inventarlas: esa sustitución requiere revisar capacidad o requisito.

Un timeout de escritura puede ocurrir después de que el proveedor haya aceptado la operación.
Reintentar sin una política de idempotencia puede duplicarla. Los casos de uso actuales son
sincrónicos y no ofrecen esa garantía: incorporar red requiere modelar resultados y límites,
además de implementar transporte. Aceptar una apelación calcula en memoria la apelación aceptada y
la corrida corregida antes de guardar nada (4.5, 4.11), pero guardar ambas, auditar y recalcular
tampoco es hoy una transacción: un fallo del adaptador de persistencia o de auditoría a mitad de esa
secuencia todavía dejaría el flujo a medias. Las reglas de unicidad que se verifican consultando el
repositorio (4.12) sólo son seguras ante concurrencia si la persistencia también las impone;
`RunResultRepository` ya lo exige en su contrato y los demás repositorios deberían sumarlo.

La reproducción histórica depende de conservar las versiones: `InMemoryRulebookRepository.save`
reemplaza una existente y la interfaz `RulebookRepository` no prohíbe ese reemplazo, aunque el flujo
normal publique una nueva. `InMemoryStandingsRepository.save` también permite sobrescribir cualquier
revisión con la misma clave. Una persistencia que exija versiones/revisiones inviolables debe
fortalecer esos contratos. Las futuras reglas deben mantener inmutable
su configuración; `record` y copias de listas no fuerzan inmutabilidad profunda de una estrategia.
Versionar parámetros tampoco congela el código ejecutable: cambiar el algoritmo de una clase de
regla podría alterar resultados históricos que la usen. Una evolución durable debe conservar la
semántica antigua, por ejemplo mediante estrategias versionadas, y probar resultados históricos
conocidos al modificar fórmulas, precisión o redondeo.

**Evidencia de evolución:** `RulebookEvolutionTest` incorpora una estrategia definida sólo en tests,
la combina con reglas existentes y publica reglamentos sucesivos. Comprueba que una ronda anterior
siga validándose y puntuándose con su versión, aunque la siguiente exija otra métrica.
`AppealRecalculationTest` cambia fórmula y desempates en un reglamento nuevo y verifica que el
recálculo posterior conserve las reglas de la tabla. Son pruebas de puntos de extensión concretos; no demuestran
compatibilidad con un proveedor HTTP todavía inexistente.

## 9. Pruebas: caminos exitosos, rechazos esperados y errores

Cada capacidad debe tener un escenario exitoso que compruebe el resultado de negocio, además de
rechazos relevantes y límites. Rechazar una inscripción inelegible es un resultado esperado con
estado y motivos; no todo camino alternativo debe lanzar una excepción.

| Capacidad | Camino exitoso | Alternativa o error cubierto | Pruebas |
| --- | --- | --- | --- |
| Configurar evento | Temporada, competencia con su reglamento `v1`, varias categorías, fechas límite, temporadas consecutivas y auditoría | Año incoherente, fechas fuera de temporada, temporada inexistente, temporadas superpuestas | `EventConfigurationUseCaseTest`, `DomainEdgeCasesTest` |
| Evolucionar reglamento | Versiones nuevas y cálculo histórico conservado | Sin desafíos o competencia inexistente | `RulebookEvolutionTest` |
| Registrar y evaluar | Equipo aceptado y guardado; homónimos con identidad propia | Rechazo guardado con violaciones tipadas; decisión repetida; integrante repetido | `RegisterTeamUseCaseTest`, `EligibilityRequirementsTest`, `TeamRegistrationTest` |
| Programar | Turnos normales, consecutivos y simultáneos con recursos independientes | Conflictos existentes o dentro del comando, fechas inválidas, equipo rechazado, de otra categoría o competencia, ordinal repetido en la categoría | `ScheduleRoundUseCaseTest`, `ScheduleConflictDetectorTest`, `DomainEdgeCasesTest` |
| Capturar | Datos válidos y último intento permitido sin reemplazar el primero; captura con posiciones provisionales | Métrica ausente, intento inválido/repetido (también en el repositorio), equipo sin turno, incidente desconocido, juez fuera del heat, criterio inexistente, juez que evalúa dos veces, categoría con posiciones definitivas | `CaptureRunResultUseCaseTest`, `RunResultTest`, `RunResultRepositoryContractTest`, `StandingsLifecycleTest`, `StandingsHistoryTest`, `ChallengeSpecTest`, `JudgeEvaluationsTest` |
| Puntuar | Fórmulas, bonos, deducciones, combinación y suma explicada | Datos ausentes con cero explicado; topes y bono no otorgado; configuración negativa, métricas o penalizaciones duplicadas, reglas sobre métricas inexistentes | `ScoringRulesTest`, `ChallengeSpecTest`, `CalculateRunScoreUseCaseTest` |
| Ordenar | Totales y desempates, incluido tiempo | Empate completo; métrica ausente en uno o ambos equipos | `RankingServiceTest` |
| Agregar intentos | Mejor intento y suma de intentos; la política viaja en el reglamento y se conserva al recalcular | Equipo sin corridas; suma y mejor intento con ganadores distintos; corrida contada dos veces | `AttemptAggregationTest`, `RankingServiceTest`, `StandingsLifecycleTest` |
| Publicar posiciones | Provisional a definitiva | Generación y publicación repetidas; apelaciones pendientes en la categoría | `StandingsLifecycleTest`, `StandingsTest`, `StandingsHistoryTest`, `AppealRecalculationTest` |
| Apelar | Aceptación con corrección que recalcula la tabla; aceptación antes de que existan posiciones; rechazo; presentación en el límite del plazo | Equipo ajeno, fuera de plazo, corrida ya apelada, corrección inválida o incidente desconocido sin cambiar apelación/corrida/posiciones, corrección fuera de orden, decisión repetida/fecha inválida | `AppealRecalculationTest`, `AppealTest`, `RunResultTest` |
| Recalcular | Nueva revisión (también tras una tabla `FINAL`) y reglas históricas | Revisiones anteriores conservadas aun publicando otro reglamento | `AppealRecalculationTest`, `StandingsRepositoryContractTest` |
| Auditar/conservar | Actor, fecha, acciones, originales y correcciones | Consultas vacías e historiales separados por categoría y competencia | Pruebas de configuración, resultados, apelación y repositorio |

Se comprueban valores, estados y efectos observables. Algunos errores previos a persistir también
verifican conservación del estado: corregir un incidente desconocido permite capturar el mismo
intento después de quitar el incidente inválido; un conflicto dentro del comando no deja reservados
los primeros turnos. Eso no implica atomicidad frente a todos los fallos posteriores.

Verificación del código actual el 9 de octubre de 2026: Maven recompiló los 187
archivos Java de producción y los 31 de pruebas, y ejecutó **193 tests, 0 fallos, 0 errores y 0
omitidos**. Es una comprobación fechada, no un total garantizado para futuras versiones.
No se establece una proporción obligatoria de tests exitosos/negativos ni se equipara cantidad con
porcentaje de cobertura.
JaCoCo 0.8.15 midió **100 % de instrucciones, ramas, líneas, complejidad, métodos y clases**. Son
10.331 instrucciones, 428 ramas, 1.873 líneas, 931 puntos de complejidad, 717 métodos y 186 clases
cubiertos. `mvn verify` genera el informe y falla si cualquiera de esos porcentajes baja del 100 %.
La suite no prueba HTTP, proveedores, SQL, transacciones o concurrencia porque esas integraciones aún
no existen; tendrán pruebas propias cuando se incorporen.
