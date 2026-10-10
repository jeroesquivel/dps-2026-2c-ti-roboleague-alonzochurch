# AGENTS.md

Guía para agentes de código que trabajen en este repositorio. Describe cómo compilar, testear y
cambiar el código sin romper las decisiones de diseño. Para el contexto funcional ver
[README.md](README.md); para el porqué de cada decisión, [DESIGN.md](DESIGN.md).

## Proyecto

Módulo de dominio de una plataforma de competencias de robótica, en Java 21 puro con Maven. No hay
frameworks, base de datos, API REST ni librería de mocking: la única dependencia es JUnit 5 en `test`.

## Comandos

```bash
mvn clean compile                       # compilar
mvn test                                # correr todos los tests
mvn test -Dtest=ScoringRulesTest        # una clase
mvn test -Dtest=ScoringRulesTest#method # un método
mvn verify                              # tests + JaCoCo con cobertura mínima del 100 %
mvn exec:java                           # ejecutar el recorrido de demo (Main)
```

**Antes de dar un cambio por terminado, `mvn verify` tiene que pasar.** JaCoCo exige 100 % de
instrucciones, ramas, líneas, complejidad, métodos y clases: todo código nuevo necesita tests que lo
recorran por completo, incluidas las ramas de error. El informe queda en
`target/site/jacoco/index.html`.

## Arquitectura

Arquitectura hexagonal (Ports & Adapters). El **núcleo** (`domain` + `application`) es dueño de
todos los puertos; los **adaptadores** quedan afuera y dependen del núcleo, nunca al revés. Todo
bajo `src/main/java/com/dps/roboleague`:

| Lado | Paquete | Contiene | Puede depender de |
| --- | --- | --- | --- |
| Núcleo | `domain.*` | entidades, value objects, reglas, servicios de dominio y **todos los puertos** | sólo dominio y JDK |
| Núcleo | `application.usecase` | interactors `*UseCase` que implementan los puertos de entrada y coordinan | dominio |
| Adaptadores de salida (driven) | `infrastructure.memory`, `infrastructure.id` | repositorios y `AuditLog` en memoria, `SequentialIdGenerator` | núcleo |
| Adaptadores de entrada (driving) | `demo`, `Main` (y los tests) | invocan los puertos de entrada | núcleo |
| Ensamblado | `infrastructure.config` | `RoboLeagueCompositionRoot`: elige adaptadores y expone puertos de entrada | todo |

Puertos:

- **De entrada (driving):** `domain.port.in`. Una interfaz por caso de uso; un adaptador de entrada
  depende de la interfaz, nunca del interactor.
- **De salida (driven):** `*Repository`, `AuditLog` e `IdGenerator`, cada uno en el paquete de su
  agregado (`domain.result.RunResultRepository`, `domain.audit.AuditLog`,
  `domain.shared.IdGenerator`). No crear un paquete `port.out` común. `java.time.Clock` se usa como
  puerto de salida del JDK.
- Un adaptador nuevo (REST, SQL, mensajería) es una implementación o un cliente de un puerto
  existente y se conecta en el composition root; no modifica el núcleo. Un adaptador de entrada
  futuro proyecta la salida de los puertos a sus propios DTOs.

Reglas que no se negocian:

- El núcleo nunca importa `infrastructure`, `demo` ni `Main`; `domain` tampoco importa
  `application`.
- Los adaptadores no se conocen entre sí ni contienen reglas de negocio: sólo traducen.
- Los casos de uso **coordinan, no deciden**: cargan agregados, invocan al dominio, persisten y
  auditan. Una regla de negocio nueva va en el dominio como un método `require…` del agregado o de
  una colección con nombre propio (`RoundResults`, `StandingsHistory`, `Appeals`…), no como un `if`
  en el caso de uso.
- El composition root sólo expone puertos de entrada; no agregar getters de repositorios.
- Ningún código llama a `Instant.now()`, `UUID.randomUUID()` ni similares: se inyectan `Clock` e
  `IdGenerator`.

## SOLID

Todo cambio respeta los cinco principios **al pie de la letra**. Si una solución obliga a violar
uno, no es la solución: hay que replantear el diseño antes de escribir código.

### SRP — Responsabilidad única

Una clase tiene **una sola razón para cambiar**, es decir, responde a un único actor.

- Un caso de uso por operación. Si un comando necesita un flag (`boolean`, enum de modo) para
  elegir entre dos comportamientos, son dos casos de uso (`AcceptAppeal` y `RejectAppeal`, no
  `ResolveAppeal` con `accepted`).
- Los casos de uso sólo coordinan: cargar, delegar en el dominio, persistir y auditar. Las reglas de
  negocio van en el agregado, en un value object o en una colección con nombre propio.
- Un value object protege una sola invariante; una colección con nombre propio, la de su conjunto.
- No mezclar en una clase cálculo de negocio con formateo, impresión o persistencia.

### OCP — Abierto/cerrado

Una variante nueva se agrega **escribiendo una clase nueva, sin modificar las existentes**.

- Los puntos de extensión son interfaces: `ScoringRule`, `TiebreakRule`, `EligibilityRule`,
  `AttemptAggregation`. Una fórmula, desempate, requisito o política nueva es una implementación
  nueva de esas interfaces.
- Prohibido `switch`/`if` encadenado sobre constantes de un enum o `instanceof` para decidir
  comportamiento. Los enums declaran un método abstracto que cada constante implementa
  (`MetricKind.accepts`, `ThresholdBonusRule.Comparison.isMetBy`), así el compilador obliga a una
  constante nueva a definir su criterio.
- No usar `default` en un `switch` para esconder casos no contemplados.

### LSP — Sustitución de Liskov

Toda implementación cumple el **contrato completo** del supertipo: la firma y también su semántica.

- No reforzar precondiciones, no debilitar postcondiciones ni lanzar excepciones que el contrato no
  contempla. Nunca implementar un método con `UnsupportedOperationException` o un cuerpo vacío
  "porque no aplica": eso indica que la interfaz está mal segregada.
- `ScoringRule`: ante un dato ausente devuelve una `ScoreContribution` de 0 con explicación, y el
  signo de lo que aporta es coherente con su `ContributionKind`. Los parámetros de configuración son
  value objects que no admiten valores que rompan esa semántica (`PointsRate`, `PointsCap`,
  `BonusPoints`, `PointsDeducted`, `MetricValue`). Toda regla nueva se suma a
  `ScoringRulesTest.everyRule`, que verifica el contrato común.
- Un adaptador de repositorio hereda el `*RepositoryContractTest` de su puerto; si no lo pasa, no es
  sustituible.

### ISP — Segregación de interfaces

Ningún cliente depende de métodos que no usa.

- Cada puerto de entrada tiene un único método `execute`.
- Los repositorios exponen sólo las consultas que algún caso de uso o servicio necesita, en el
  lenguaje del negocio. Sin `Repository<T, ID>` genérico ni CRUD uniforme.
- **Sin métodos "por si acaso"**: un método sin cliente en código de producción se elimina, aunque
  lo use un test. Evitar métodos `default` en interfaces para ahorrar código; si una
  implementación no necesita un método, la interfaz está mal partida.

### DIP — Inversión de dependencias

Los módulos de alto nivel dependen de abstracciones **definidas por el dominio**, nunca de
implementaciones.

- Los casos de uso y servicios de dominio reciben repositorios, `AuditLog`, `IdGenerator` y `Clock`
  por constructor, tipados como la interfaz.
- Las implementaciones concretas se instancian **sólo en `RoboLeagueCompositionRoot`**. Fuera de
  ahí, `new` se usa para agregados, value objects y colecciones del dominio, no para colaboradores.
- Sin singletons, estado estático mutable ni service locators.
- Las dependencias ambientales también se invierten: nada de `Instant.now()`, `LocalDate.now()` ni
  `UUID.randomUUID()` en el código de producción.

### Verificación antes de terminar

Repasar cada clase nueva o modificada contra los cinco principios y, si el cambio introduce o mueve
una decisión SOLID, documentarla en `DESIGN.md` con su **Patrón / principio** correspondiente.

## Convenciones de código

- **Idioma:** identificadores, mensajes de excepción y nombres de tests en inglés; documentación
  (`README.md`, `DESIGN.md`, `.docs/`) en español.
- **Casos de uso:** una interfaz en `domain.port.in` con un único método `execute`. Varias entradas
  van en un `record Command` anidado; las búsquedas reciben directamente el id tipado. La
  implementación es `public final class XxxUseCase` con colaboradores `private final` inyectados por
  constructor.
- **Value objects:** `record` que valida su invariante en el constructor compacto
  (`Objects.requireNonNull` + `InvalidValueException`) y ofrece fábricas `of(...)`. No pasar
  primitivos sueltos por el dominio: hay ids tipados (`TeamId`, `RunId`…), `Points`, `MetricValue`,
  `PointsRate`, `PointsCap`, etc.
- **Agregados inmutables:** campos `final`; cada transición devuelve una instancia nueva mediante un
  constructor privado. El constructor público crea sólo el estado inicial.
- **Colecciones con nombre propio** en lugar de `List<X>` cuando la colección tiene una invariante.
- **Enums con comportamiento por constante** (método abstracto implementado en cada constante) en
  lugar de `switch (this)`.
- **Excepciones:** usar la categoría correcta de `domain.shared.DomainException`:

  | Excepción | Cuándo |
  | --- | --- |
  | `InvalidValueException` | un valor o configuración no cumple su invariante |
  | `RuleViolationException` | datos bien formados violan una regla de negocio |
  | `ConflictException` | la operación choca con el estado actual |
  | `NotFoundException` | el elemento referenciado no existe (`NotFoundException.of("Round", id)`) |

- **Reglas de puntaje:** cada `ScoringRule` devuelve una `ScoreContribution` explicable, aunque
  aporte 0, y declara sus `referencedMetrics()`. Una regla nueva debe sumarse al proveedor
  parametrizado `ScoringRulesTest.everyRule`.
- No agregar métodos a interfaces "por si acaso": cada método de un puerto necesita un cliente.

## Tests

- JUnit 5 en `src/test/java`, espejando la estructura de paquetes de `main`.
- Nombres de método que describen el comportamiento en inglés:
  `capturesTheRunPinningTheRulebookVersionInForce`.
- **Sin mocking framework.** Los tests de dominio construyen objetos directamente; los de casos de
  uso usan `support/TestEdition` (composition root en memoria, reloj fijo, ids secuenciales). Si hace
  falta un doble, escribirlo a mano. Para el paso del tiempo, `support/AdjustableClock`.
- Cuando el contrato de un repositorio incluye una regla de negocio, se fija en un test de contrato
  abstracto (`*RepositoryContractTest`) que hereda cada adaptador.
- Cubrir caminos exitosos, rechazos esperados (verificando el tipo de excepción) y, cuando aplique,
  que un rechazo no guarda ni audita nada.

## Documentación

- Un cambio de diseño (patrón, regla movida de capa, alternativa descartada) se refleja en la
  sección correspondiente de `DESIGN.md`, siguiendo su formato: **Patrón / principio**, **Dónde**,
  **Por qué**, **Alternativas descartadas**.
- Las correcciones de las entregas se siguen en `.docs/correcciones-entrega-*.md`. Al resolver una,
  actualizar su **Estado** (`Pendiente` · `En curso` · `Resuelta` · `Descartada`) e indicar dónde
  quedó resuelta (sección de `DESIGN.md` y tests).

## Commits

Mensajes cortos con prefijo en mayúsculas según el tipo de cambio, p. ej. `ADD PointsCap, BonusPoints
and PointsDeducted` o `FIX correcciones entrega1 - Dominio`. No commitear `target/`.
