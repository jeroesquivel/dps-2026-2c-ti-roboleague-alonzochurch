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
| Dominio | `domain.*` | Entidades, value objects, reglas y servicios de dominio | Nada fuera del dominio y del JDK |
| Aplicación | `application.*` | Casos de uso y contratos | Dominio |
| Detalles | `infrastructure.*`, `demo`, `Main` | Adaptadores en memoria, composition root y ejecución de ejemplo | Aplicación, dominio y otros componentes de detalles |

El dominio no importa ninguna clase de `application` ni de `infrastructure`: la dirección de las
dependencias es siempre hacia el centro. Las reglas de negocio (puntaje, elegibilidad, desempates,
conflictos de agenda) quedan expresadas en clases que no conocen persistencia, frameworks ni una
interfaz externa de entrada/salida.

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

**Dónde:** `application/port/in/*` (interfaces) y `application/usecase/*UseCase` (implementaciones).

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

### 1.3 Repositorios declarados por la aplicación e implementados afuera

**Patrón / principio:** Repository, DIP.

**Dónde:** interfaces en `application/port/out/*`, implementaciones en `infrastructure/memory/*`.

Los repositorios están expresados en el lenguaje del negocio (`findLatest(competitionId,
categoryId)`) y exponen agregados, `Optional` y listas, nunca filas ni estructuras de base de datos.
Los otros puertos de salida son `AuditLog`, para registrar/consultar eventos, e `IdGenerator`, para
obtener identificadores tipados.

Cuando el contrato de un puerto incluye una regla de negocio —"publicar reemplaza la revisión
provisional, las anteriores no se tocan"— se verifica con un test de contrato abstracto,
`StandingsRepositoryContractTest`. Actualmente lo hereda `InMemoryStandingsRepositoryTest`; un
adaptador futuro debería reutilizarlo y sumar sus pruebas de integración. La firma de
`StandingsRepository` por sí sola no impone esa política: el test fija el comportamiento esperado.

**Por qué:** la aplicación define los contratos que necesita en términos del dominio, y la
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
colaboradores por constructor; las entidades pueden tener mutaciones protegidas por métodos de
negocio. **Sus métodos de acceso a casos de uso sólo devuelven puertos de entrada:** no hay getters
de repositorios. Algunas consultas sí devuelven entidades mutables, por lo que un futuro adaptador
de API deberá proyectar su salida a DTOs y mantener las modificaciones dentro de los casos de uso.

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

`ScoringRule` declara la firma `apply(ScoringContext)`, `referencedMetrics()` (las métricas que la
regla lee, ver 2.2) y el helper por defecto `breakdownFor`; no incluye una especificación textual
del contrato ni lo fuerza a nivel de tipos. El comportamiento
común implementado y comprobado para las siete reglas actuales es: **con un contexto válido, emitir
al menos una contribución explicada y devolver cero ante la ausencia de la medición, evaluación o
incidente que corresponda**, en lugar de lanzar por esa ausencia.

**Por qué:** un cliente puede aplicar una `ScoringRule` sin conocer su implementación. Para conservar
la sustituibilidad respecto del comportamiento esperado, una regla nueva debe respetar ese manejo
de datos ausentes. Un test parametrizado en
`ScoringRulesTest` recorre las siete reglas simples de su proveedor `everyRule()` y verifica el
contrato. Para comprobar una regla nueva hay que agregarla explícitamente a ese proveedor.

**Consecuencia sobre dónde se valida:** tolerar datos ausentes al puntuar no reemplaza las validaciones
del desafío. `CaptureRunResultUseCase` y la validación de correcciones en `ResolveAppealUseCase`
invocan `ChallengeSpec.validate` y `validateIncidents`. La fuente de configuración del catálogo es
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

**Dónde:** `domain/rulebook/Rulebook`, `RulebookVersion` y `Rulebook.of`.

Un `Rulebook` reúne los desafíos, la política de elegibilidad, la política de agregación de intentos
(3.5) y los criterios de desempate de una versión. En el flujo normal, publicar un reglamento no modifica el anterior:
`PublishRulebookUseCase` crea la versión siguiente y `Competition.activateRulebook` sólo acepta
versiones que superen a la vigente.
`PublishRulebookUseCase` ya recibe todos los componentes juntos y los pasa a `Rulebook.of`, que
indexa los desafíos por identificador y construye el reglamento. Si un identificador se repite, lanza
`InvalidValueException`: un desafío no puede pisar en silencio a otro. El constructor mantiene
las validaciones y las copias defensivas del mapa de desafíos y la lista de desempates.

**Por qué:** el enunciado exige poder recalcular resultados con exactamente la versión de reglas
correspondiente. El record y sus copias defensivas evitan modificar sus colecciones; las estrategias
actuales conservan su configuración inmutable. La fábrica evita el estado intermedio de un builder
porque el caso de uso ya tiene todos los datos disponibles. Las interfaces de estrategia no fuerzan
inmutabilidad profunda y el almacenamiento no bloquea reemplazos de versiones (ver sección 8).

**Alternativas descartadas:** un builder para volver a reunir datos que ya llegan juntos; un
reglamento mutable con historial de cambios (cualquier corrección alteraría resultados ya
publicados); guardar sólo la versión vigente (haría imposible el recálculo histórico).

### 3.2 La versión de reglas se fija en la ronda y viaja con el resultado

**Patrón / principio:** Snapshot de configuración.

**Dónde:** `Round.rulebookVersion`, `RunResult.rulebookVersion`, `CategoryScoringService`.

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

**Dónde:** `GenerateStandingsUseCase`, `PublishStandingsUseCase`, `RecalculateStandingsUseCase` y
`domain/ranking/Standings`.

`Standings` es inmutable y lleva número de revisión (`Revision`) y estado (`PROVISIONAL` / `FINAL`).
Su constructor es privado: las únicas formas de obtener una revisión son `Standings.provisional`
(revisión 1), `publish()` y `supersede()`, así no se puede armar una revisión `FINAL` sin publicar.
Por eso dejó de ser un `record` (el constructor canónico de un record no puede ser más restrictivo
que el tipo). `publish()` falla si ya es definitiva; `supersede()` abre una revisión nueva
provisional conservando la versión de reglamento. Generar dos veces la misma tabla se rechaza con un mensaje que indica usar el
recálculo, y el repositorio conserva todas las revisiones.

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
implementaciones del mismo puerto signifiquen cosas distintas.

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

El reglamento exige una política (el constructor rechaza `null`), `PublishRulebook.Command` la
recibe junto con los desafíos y desempates, y `GenerateStandingsUseCase` /
`RecalculateStandingsUseCase` la pasan a `CategoryScoringService.collect`, que arma con ella el
`TeamRuns` de cada equipo: la colección de corridas puntuadas es el lugar donde vive la política
(ver 4.10). Como el reglamento está versionado, una tabla se genera y se recalcula con la
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

**Patrón / principio:** Specification, Composite, OCP.

**Dónde:** `domain/eligibility/EligibilityRule`, `EligibilityPolicy` y las reglas `AgeRangeRule`,
`TeamCompositionRule`, `RobotClassRule`, `RobotSpecificationRule`, `RequiredDocumentsRule`.

Cada restricción es una regla que devuelve la lista de violaciones que encuentra. `EligibilityPolicy`
es a la vez una regla y la composición de todas: `evaluate` concatena las listas de violaciones y
`verdictFor` las envuelve en un `EligibilityVerdict`. No se corta en la primera violación.

**Por qué:** un equipo debe recibir de una sola vez todo lo que tiene que corregir. Además, las
restricciones varían por edición y categoría, así que se configuran en el reglamento en lugar de
estar cableadas en el caso de uso. La categoría aporta edades y clase de robot; el registro usa como
fecha de referencia el inicio de la competencia.

**La decisión la toma el agregado.** `TeamRegistration.resolveWith(EligibilityVerdict)` acepta la
inscripción si el veredicto es elegible y la rechaza en caso contrario; ya no existen `accept()` ni
`reject(...)` públicos, así que una inscripción inelegible no puede quedar `ACCEPTED` y
`RegisterTeamUseCase` no tiene un `if` sobre el veredicto. Resolver dos veces lanza
`ConflictException`.

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

**Dónde:** `domain/schedule/ScheduleConflictDetector`, usado por `ScheduleRoundUseCase`.

Detectar que una pista, un equipo o un juez ya están ocupados requiere mirar turnos de varias rondas,
así que la regla no pertenece a ninguna entidad. El caso de uso reúne los turnos existentes y el
servicio decide; `Round` conserva la invariante que sí le corresponde (un equipo no puede tener dos
turnos en la misma ronda).

`ScheduleRoundUseCase` reúne los turnos de la misma competencia y también incorpora los candidatos
ya aceptados dentro del comando. No busca reservas en otras competencias. `TimeSlot.overlaps`
permite que un turno empiece exactamente cuando termina otro.

**Por qué:** mantiene la regla en el dominio y testeable sin repositorios, sin forzarla dentro de una
entidad que no tiene toda la información.

Que un turno caiga dentro de las fechas de la competencia es una invariante distinta y vive donde
están esas fechas: `Competition.requireDateWithinPeriod`. El caso de uso la invoca por cada turno, de
inicio y de fin. Un tipo de conflicto es un `ScheduleConflictType`, no un `String`. Cuando hay
conflictos, el caso de uso lanza `ScheduleConflictException` (una `ConflictException`, ver 4.9), que
expone la lista de `ScheduleConflict` detectados.

**Alternativas descartadas:** poner la detección de conflictos en el caso de uso (mezcla orquestación
con negocio y dificulta reutilizar la regla de forma aislada); ponerla en `Round` (no ve los turnos
de las demás rondas); dejar el período de la competencia como dato decorativo, lo que permitiría
agendar turnos en fechas ajenas al evento.

### 4.3 Auditoría: el resultado conserva el original y todas las modificaciones

**Patrón / principio:** historial de correcciones dentro del agregado.

**Dónde:** `domain/result/RunResult`, `CorrectionHistory` y `ResultCorrection`.

`RunResult` guarda las mediciones e incidentes originales y un `CorrectionHistory`; las mediciones
vigentes son las de la última corrección del historial. El historial exige orden cronológico: una
corrección no puede ser anterior a la previa. Cada corrección registra momento, responsable (`Actor`),
motivo y un `Optional<AppealId>` de origen; el flujo de resolución usa `ResultCorrection.fromAppeal`
para completarlo. Una corrección no puede ser anterior a la captura. Las evaluaciones de jueces
se conservan como fueron capturadas: el modelo de corrección actual modifica mediciones e incidentes,
no esas evaluaciones.

**El agregado valida la corrección contra su desafío.** `RunResult.applyCorrection(correction,
challenge)` rechaza el `ChallengeSpec` de otro desafío y valida las mediciones e incidentes
corregidos con `ChallengeSpec.validate`/`validateIncidents` antes de agregarla al historial. Así no se
puede corregir una corrida con datos que su propio desafío rechazaría, aunque se llame al método por
fuera de `ResolveAppealUseCase`.

**Por qué:** cumple "conservar los valores originales y todas las modificaciones" dentro del modelo,
no en una bitácora externa que podría desincronizarse. El recálculo usa siempre los valores vigentes
y la investigación puede reconstruir el camino completo.

**Alternativas descartadas:** actualizar las mediciones en el lugar y anotar el cambio en un log
(pierde la trazabilidad dentro del agregado); event sourcing completo del resultado (ver 5.1).

### 4.4 Bitácora de auditoría como puerto

**Patrón / principio:** DIP, bitácora de auditoría mediante un puerto explícito.

**Dónde:** `application/port/out/AuditLog`, `domain/audit/AuditEvent` y `AuditAction`.

Cada caso de uso que modifica estado registra un `AuditEvent` tipado: la acción es un `AuditAction`,
el sujeto es el `Identifier` del elemento afectado (`RunId`, `TeamId`, `CategoryId`…), el responsable
es un `Actor` y los detalles son un `Map<AuditDetail, String>` con claves de un enum. Como el sujeto
es un id tipado, `AuditLog.findBySubject(Identifier)` no confunde una corrida con una categoría que
tenga el mismo texto. Los valores de detalle se generan desde el dominio: `MeasurementSet.toString()`
describe las mediciones ordenadas por clave (el `toString` de un `Map.copyOf` no tiene orden
definido). Los tests verifican, por ejemplo, que aceptar una apelación **con corrección** deja
`RESULT_CORRECTED`. Aceptarla sin corrección registra la decisión, sin modificar la corrida ni
recalcular automáticamente las posiciones.

**Por qué:** la auditoría atraviesa todos los casos de uso y debe poder apuntar mañana a un archivo o
a una base sin tocar el negocio.

**Alternativas descartadas:** eventos de dominio publicados por las entidades con un bus (potente pero
prematuro sin infraestructura asincrónica); registrar la auditoría en el adaptador de persistencia
(perdería el motivo y el responsable de la acción).

### 4.5 Apelaciones como agregado con transiciones protegidas

**Patrón / principio:** máquina de estados en la entidad, encapsulamiento de invariantes.

**Dónde:** `domain/appeal/Appeal`, `ResolveAppealUseCase`.

Una apelación sólo puede resolverse una vez y la decisión no puede ser anterior a su presentación. Si
se acepta con una corrección, el caso de uso valida las mediciones corregidas contra el desafío del
reglamento fijado en la corrida antes de aplicarlas.

**Por qué:** las reglas de transición viven en la entidad, no en el caso de uso, que sólo orquesta. Y
una corrección no puede introducir datos que el propio desafío rechazaría.

Si se acepta con una corrección, el caso de uso valida **las mediciones y los códigos de incidente
antes** de mutar: resuelve la corrección contra el desafío y recién después acepta la apelación.
`RunResult.applyCorrection` vuelve a validarla (ver 4.3): la validación anticipada evita dejar la
apelación aceptada y la corrida sin corregir; la del agregado protege la invariante para cualquier
llamador.
Si validara después, una corrección inadmisible dejaría la apelación
aceptada y la corrección sin aplicar, dos estados incompatibles en el mismo flujo. `TeamRegistration`
protege sus transiciones con el mismo criterio que `Appeal`: una inscripción se decide una sola vez.

Esto no equivale a validar todo el comando por adelantado ni a ofrecer una transacción: construir la
corrección o registrar auditoría todavía puede fallar después de resolver la apelación (ver sección 8).

**Alternativas descartadas:** un campo de estado editable desde afuera (cualquier código podría dejar
la apelación en un estado inconsistente); mutar primero y validar después confiando en que la
ausencia de `save()` alcanza para descartar el cambio, que sólo es cierto mientras el adaptador no
devuelva la instancia viva que acaba de mutarse.

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

**Patrón / principio:** DRY, servicio de aplicación.

**Dónde:** `application/service/CategoryScoringService`.

Recorre las rondas de una categoría, puntúa cada corrida con su reglamento fijado y arma los
`TeamScoreSummary` que consume `RankingService`. `GenerateStandingsUseCase` y
`RecalculateStandingsUseCase` usan `collect`; `CalculateRunScoreUseCase` reutiliza `scoreRun` para una
corrida individual. `collect` recibe la `AttemptAggregation` del reglamento y arma con ella el
`TeamRuns` de cada `TeamScoreSummary`, que reúne todos los intentos capturados de todas las rondas de
la categoría; `TeamRuns.aggregatedPoints` delega en la política para decidir cuáles cuentan (ver 3.5). Un equipo sin corridas
capturadas no aparece en la colección ni en el ranking generado.

**Por qué:** si cada caso de uso armara la tabla por su cuenta, generar y recalcular podrían divergir,
que es exactamente el error que el requisito de recálculo busca evitar.

**Alternativas descartadas:** duplicar el recorrido en cada caso de uso; ubicarlo en el dominio, que lo
obligaría a conocer repositorios.

### 4.9 Jerarquía de excepciones del dominio

**Patrón / principio:** excepciones por categoría de falla, Open/Closed.

**Dónde:** `domain/shared/DomainException` (abstracta) y sus subclases `InvalidValueException`,
`RuleViolationException`, `ConflictException` y `NotFoundException`; `ScheduleConflictException`
extiende `ConflictException`.

| Excepción | Significa | Ejemplos |
| --- | --- | --- |
| `InvalidValueException` | un valor o una configuración no cumple su invariante | id vacío, puntaje de juez fuera de 0–10, métrica declarada dos veces, `PointsRate` negativo |
| `RuleViolationException` | una operación con datos bien formados viola una regla de negocio | intento fuera del límite, medición que el desafío no define, juez fuera del heat, turno fuera del período |
| `ConflictException` | la operación choca con el estado actual | apelación o inscripción ya resueltas, posiciones ya definitivas o ya generadas, intento ya capturado, conflicto de agenda |
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
`domain/schedule/Heats` y `domain/team/TeamMembers`.

| Colección | Reemplaza a | Invariante que concentra |
| --- | --- | --- |
| `JudgeEvaluations` | `List<JudgeEvaluation>` | un juez evalúa una vez cada criterio; `requireEvaluatorsWithin(panel)` |
| `TeamRuns` | `List<ScoredRun>` + agregación en `TeamScoreSummary` | una corrida cuenta una sola vez; es donde se aplica la `AttemptAggregation` |
| `CorrectionHistory` | `List<ResultCorrection>` | orden cronológico; la última corrección es la vigente |
| `Heats` | `List<Heat>` en `Round` | un equipo tiene un único turno por ronda |
| `TeamMembers` | `List<Member>` | al menos un integrante, sin integrantes repetidos; `competitors()`, `hasCoach()` |

**Evaluaciones de jueces.** Antes un mismo juez podía evaluar dos veces el mismo criterio (con
`J1=10, J1=10, J2=0` el promedio daba 6,67 en lugar de 5) y no se verificaba que perteneciera al
heat. Ahora `JudgeEvaluations` rechaza la repetición al construirse y
`ChallengeSpec.validateEvaluations(evaluations, heat.judges())` verifica que cada criterio sea una
métrica `JUDGE_CRITERION` del desafío y que cada juez esté asignado al heat. `CaptureRunResultUseCase`
la invoca junto con las demás validaciones del desafío.

**Por qué:** además de aportar lenguaje ubicuo, cada colección es el único lugar donde se puede
romper su invariante, en vez de repetir la verificación en cada consumidor.

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

No se aplicó: aceptar una apelación no emite un evento que dispare el recálculo; el recálculo es un
caso de uso explícito que el operador invoca.

**Consecuencia:** la cadena corrección → recálculo → publicación queda en manos de quien orquesta, y no
hay reacción automática. A cambio, el flujo es explícito, sincrónico y fácil de auditar, sin
infraestructura de mensajería que esta entrega no tiene.

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
escrituras para comprobar que el rechazo no guarda ni audita una apelación.

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

No se agregan operaciones anticipando clientes inexistentes. `ScoringRule` declara `apply`,
`referencedMetrics` (cuyo cliente es la validación de `ChallengeSpec`, ver 2.2) y el helper por
defecto `breakdownFor`; `EligibilityRule` sólo declara `evaluate`. Ninguna de las dos
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
sublista, sin cambiar la interfaz. Esto no elimina Composite de todo el sistema:
`EligibilityPolicy` sí implementa `EligibilityRule` y compone otras reglas de elegibilidad.

## 6. Cobertura de los requisitos obligatorios

| Capacidad | Dónde se resuelve |
| --- | --- |
| Configuración del evento | `CreateSeasonUseCase`, `CreateCompetitionUseCase`, `Season`, `Competition`, `Category` |
| Registro de equipos | `RegisterTeamUseCase`, `TeamRegistration`, `TeamMembers`, `Member`, `Robot`, `Weight`, `TeamDocument` |
| Elegibilidad | `EligibilityPolicy` y las reglas de `domain/eligibility/rule` |
| Configuración de desafíos | `ChallengeSpec`, `MetricDefinition`, `domain/scoring/rule/*`, `PenaltyDefinition`, `PointsRate`, `PointsAmount` |
| Programación | `ScheduleRoundUseCase`, `Round`, `Heat`, `TimeSlot`, `ScheduleConflictDetector`, `Competition.requireDateWithinPeriod` |
| Captura de resultados | `CaptureRunResultUseCase`, `RunResult`, `MeasurementSet`, `JudgeEvaluations`, `JudgeScore`, `IncidentReport` |
| Cálculo explicable | `CalculateRunScoreUseCase`, `ScoreBreakdown`, `ScoreContribution`, `ContributionKind` |
| Ranking | `RankingService`, `TeamRuns`, `AttemptAggregation`, `domain/ranking/aggregation/*`, `TiebreakRule`, `domain/ranking/rule/*` y `AppliedTiebreak` |
| Publicación | `Standings`, `PublicationStatus`, `GenerateStandingsUseCase`, `PublishStandingsUseCase`, `GetStandingsUseCase` |
| Apelaciones | `Appeal`, `SubmitAppealUseCase`, `ResolveAppealUseCase` |
| Recálculo | `RecalculateStandingsUseCase`, `CategoryScoringService` |
| Auditoría | `CorrectionHistory`, `Standings.revision()`, `AuditLog`, `AuditEvent`, `AuditDetail`, `Actor`, `FindAuditTrailUseCase` |

## 7. Estrategia de pruebas

Los tests unitarios cubren las reglas donde vive el negocio: cálculo de cada criterio de puntaje y su
composición, validación de mediciones contra el desafío, elegibilidad, desempates y posiciones
compartidas, conflictos de agenda, historial de correcciones y transiciones de apelaciones y
publicación.

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
tabla, incluido el caso en que la corrección se rechaza y la apelación queda sin resolver.

`StandingsRepositoryContractTest` fija el comportamiento esperado del puerto de posiciones;
`InMemoryStandingsRepositoryTest` lo hereda. Los adaptadores futuros deberían reutilizar esa suite;
no existe un mecanismo que fuerce automáticamente esa herencia.

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
| Memoria se reemplaza por SQL u otro almacenamiento | Adaptadores de repositorio y composition root | Preservar versiones/revisiones, ausencia y orden; reutilizar tests de contrato y agregar integración real |
| Cambia reloj o formato de identificadores | `Clock` o implementación de `IdGenerator` | No usar el texto del ID como desempate de negocio; distinguirlo del orden técnico de empates completos (3.4); usar reloj controlado en pruebas |
| Cambia un coeficiente o umbral | Configuración de una nueva versión del reglamento | Probar resultado esperado y conservación del cálculo anterior |
| Aparece una fórmula nueva | Nueva `ScoringRule`, configuración y pruebas | Conservar contribuciones explicadas, tipos y comportamiento con datos ausentes |
| Aparece una restricción o desempate | Nueva `EligibilityRule` o `TiebreakRule` | Verificar composición, prioridad y contratos |
| Cambia cómo cuentan los intentos (mejor intento, suma, promedio) | Nueva versión del reglamento con otra `AttemptAggregation` | Probar escenarios donde las políticas producen ganadores distintos y la conservación de la tabla anterior |
| Cambian permisos, plazos o etapas de apelación | Reglas, casos de uso y, si corresponde, estados de dominio | Probar transiciones permitidas y prohibidas; el DTO HTTP no decide estas políticas |

Una API de entrada debería recorrer `HTTP DTO → mapper → entrada tipada → caso de uso` y mapear la
respuesta a un DTO propio. Serializar entidades mutables como contrato público acoplaría la API
al modelo interno. La entrada tipada puede ser un `Command` o el ID que recibe una consulta. El mapper
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
además de implementar transporte. Guardar apelación, corregir corrida y auditar tampoco es hoy
una transacción. El actor ya llega validado como `Actor` en el comando, pero un fallo del adaptador
de persistencia o de auditoría después de mutar todavía dejaría el flujo a medias.

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
recálculo anterior conserve sus reglas. Son pruebas de puntos de extensión concretos; no demuestran
compatibilidad con un proveedor HTTP todavía inexistente.

## 9. Pruebas: caminos exitosos, rechazos esperados y errores

Cada capacidad debe tener un escenario exitoso que compruebe el resultado de negocio, además de
rechazos relevantes y límites. Rechazar una inscripción inelegible es un resultado esperado con
estado y motivos; no todo camino alternativo debe lanzar una excepción.

| Capacidad | Camino exitoso | Alternativa o error cubierto | Pruebas |
| --- | --- | --- | --- |
| Configurar evento | Temporada, competencia, varias categorías, fechas límite y auditoría | Año incoherente, fechas fuera de temporada, temporada inexistente | `EventConfigurationUseCaseTest` |
| Evolucionar reglamento | Versiones nuevas y cálculo histórico conservado | Sin desafíos o competencia inexistente | `RulebookEvolutionTest` |
| Registrar y evaluar | Equipo aceptado y guardado; homónimos con identidad propia | Rechazo guardado con violaciones tipadas; decisión repetida; integrante repetido | `RegisterTeamUseCaseTest`, `EligibilityPolicyTest`, `TeamRegistrationTest` |
| Programar | Turnos normales, consecutivos y simultáneos con recursos independientes | Conflictos existentes o dentro del comando, fechas inválidas, equipo rechazado | `ScheduleRoundUseCaseTest`, `ScheduleConflictDetectorTest` |
| Capturar | Datos válidos y último intento permitido sin reemplazar el primero | Métrica ausente, intento inválido/repetido, equipo sin turno, incidente desconocido, juez fuera del heat, criterio inexistente, juez que evalúa dos veces | `CaptureRunResultUseCaseTest`, `ChallengeSpecTest`, `JudgeEvaluationsTest` |
| Puntuar | Fórmulas, bonos, deducciones, combinación y suma explicada | Datos ausentes con cero explicado; topes y bono no otorgado; configuración negativa, métricas o penalizaciones duplicadas, reglas sobre métricas inexistentes | `ScoringRulesTest`, `ChallengeSpecTest`, `CalculateRunScoreUseCaseTest` |
| Ordenar | Totales y desempates, incluido tiempo | Empate completo; métrica ausente en uno o ambos equipos | `RankingServiceTest` |
| Agregar intentos | Mejor intento y suma de intentos; la política viaja en el reglamento y se conserva al recalcular | Equipo sin corridas; suma y mejor intento con ganadores distintos; corrida contada dos veces | `AttemptAggregationTest`, `RankingServiceTest`, `StandingsLifecycleTest` |
| Publicar posiciones | Provisional a definitiva | Generación y publicación repetidas | `StandingsLifecycleTest`, `StandingsTest` |
| Apelar | Aceptación con corrección y sin corrección | Rechazo, equipo ajeno, corrección inválida (también aplicada directo sobre el agregado), corrección fuera de orden, decisión repetida/fecha inválida | `AppealRecalculationTest`, `AppealTest`, `RunResultTest` |
| Recalcular | Nueva revisión y reglas históricas | Revisiones anteriores conservadas aun publicando otro reglamento | `AppealRecalculationTest`, `StandingsRepositoryContractTest` |
| Auditar/conservar | Actor, fecha, acciones, originales y correcciones | Consultas vacías e historiales separados por categoría y competencia | Pruebas de configuración, resultados, apelación y repositorio |

Se comprueban valores, estados y efectos observables. Algunos errores previos a persistir también
verifican conservación del estado: corregir un incidente desconocido permite capturar el mismo
intento después de quitar el incidente inválido; un conflicto dentro del comando no deja reservados
los primeros turnos. Eso no implica atomicidad frente a todos los fallos posteriores.

Verificación del código actual el 7 de octubre de 2026: Maven recompiló los 178
archivos Java de producción y los 27 de pruebas, y ejecutó **156 tests, 0 fallos, 0 errores y 0
omitidos**. Es una comprobación fechada, no un total garantizado para futuras versiones.
No se establece una proporción obligatoria de tests exitosos/negativos ni se equipara cantidad con
porcentaje de cobertura.
JaCoCo 0.8.15 midió **100 % de instrucciones, ramas, líneas, complejidad, métodos y clases**. Son
9.512 instrucciones, 395 ramas, 1.743 líneas, 864 puntos de complejidad, 666 métodos y 173 clases
cubiertos. `mvn verify` genera el informe y falla si cualquiera de esos porcentajes baja del 100 %.
La suite no prueba HTTP, proveedores, SQL, transacciones o concurrencia porque esas integraciones aún
no existen; tendrán pruebas propias cuando se incorporen.
