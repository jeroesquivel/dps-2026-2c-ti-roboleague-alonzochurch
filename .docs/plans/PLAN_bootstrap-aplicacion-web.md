# Plan: Bootstrap de la aplicación web

Preparar la estructura ejecutable de RoboLeague para incorporar API REST y frontend, separando el
core Java puro del backend Spring Boot sin modificar el comportamiento funcional existente. Este es
un plan técnico de una sola ejecución; las funcionalidades se implementan después mediante las
specs de `.docs/specs/`.

## 1. Fuentes de verdad

Antes de ejecutar el plan, leer:

- [`AGENTS.md`](../../AGENTS.md), para límites globales y comandos;
- [`architecture.md`](../../architecture.md), para la estructura objetivo y decisiones de la
  aplicación web;
- [`DESIGN.md`](../../DESIGN.md), para no alterar decisiones del núcleo;
- [skill `evolve-core`](../../.agents/skills/core/evolve-core/SKILL.md), sólo si un movimiento exige
  tocar `domain` o `application`;
- [skill `add-rest-endpoint`](../../.agents/skills/backend/add-rest-endpoint/SKILL.md), cuando se
  agregue el smoke endpoint indicado en este plan.

No implementar durante este bootstrap las specs funcionales de `.docs/specs/`. El primer flujo real
se elegirá después de que la estructura base quede verificada.

## 2. Estado actual

El repositorio es un único proyecto Maven Java 21:

```text
src/main/java/com/dps/roboleague/
|-- domain/
|-- application/
|-- infrastructure/
|   |-- config/
|   |-- id/
|   `-- memory/
|-- demo/
`-- Main.java
```

Características actuales:

- `domain` y `application` forman el núcleo hexagonal;
- `infrastructure.memory` implementa los repositorios y `AuditLog` en memoria;
- `infrastructure.id` implementa `SequentialIdGenerator`;
- `RoboLeagueCompositionRoot` ensambla manualmente el módulo;
- `Main` ejecuta `DemoScenario` con datos fijos;
- no hay Spring, API HTTP ni frontend;
- los tests y JaCoCo se ejecutan desde el `pom.xml` raíz;
- la cobertura exigida es 100 % para el código del núcleo actual.

## 3. Estado objetivo

El repositorio queda organizado como proyecto Maven multimódulo:

```text
roboleague/
|-- pom.xml
|-- core/
|   |-- pom.xml
|   `-- src/
|       |-- main/java/com/dps/roboleague/
|       |   |-- domain/
|       |   `-- application/
|       `-- test/java/com/dps/roboleague/
|-- backend/
|   |-- pom.xml
|   `-- src/
|       |-- main/java/com/dps/roboleague/
|       |   |-- RoboLeagueApplication.java
|       |   |-- adapter/
|       |   |   |-- in/web/
|       |   |   `-- out/
|       |   |       |-- id/
|       |   |       `-- memory/
|       |   `-- config/
|       `-- test/java/com/dps/roboleague/
`-- frontend/
```

Dependencias permitidas:

```text
backend  ---> core
frontend ---> backend únicamente mediante HTTP
core     -X-> backend, Spring, HTTP, Jackson o frontend
```

El frontend puede comenzar como una carpeta reservada con la configuración mínima de la tecnología
elegida sólo si esa tecnología ya está decidida. Si todavía no lo está, crear únicamente el
directorio cuando Git pueda conservarlo mediante un archivo con propósito explícito; no inventar un
stack ni agregar boilerplate especulativo.

## 4. Alcance

Este plan incluye:

1. Convertir el build raíz en un agregador Maven.
2. Crear el módulo `core` con el dominio y la aplicación actuales.
3. Crear el módulo `backend` Spring Boot.
4. Reubicar los adaptadores en memoria y de identificadores en el backend.
5. Configurar como beans los repositorios, `AuditLog`, `IdGenerator`, `Clock`, servicios y puertos de
   entrada existentes.
6. Conservar un mecanismo de composición manual para los tests del core cuando siga siendo útil.
7. Agregar una comprobación HTTP técnica mínima de disponibilidad, sin exponer funcionalidad del
   negocio.
8. Agregar pruebas de límites y de arranque suficientes para verificar la nueva estructura.
9. Actualizar documentación y comandos para reflejar el build multimódulo.

## 5. Fuera de alcance

- Implementar los flujos funcionales de las specs de `.docs/specs/`.
- Diseñar contratos REST de temporadas, equipos, rondas, resultados, standings o apelaciones.
- Agregar JPA, Spring Data, una base de datos o migraciones.
- Agregar autenticación, autorización o Spring Security.
- Elegir o construir el frontend si el equipo todavía no decidió su tecnología.
- Cambiar reglas de puntaje, elegibilidad, agenda, resultados, ranking o apelaciones.
- Refactorizar clases del dominio sólo para aprovechar características de Spring.
- Agregar microservicios, mensajería, contenedores o despliegue en la nube.
- Incorporar endpoints genéricos, CRUD o consultas “por si acaso”.

## 6. Decisiones obligatorias

### 6.1 El core continúa siendo Java puro

El `pom.xml` de `core` no incorpora Spring Boot, Spring Framework, Jackson, Jakarta Validation ni
dependencias web. JUnit y JaCoCo permanecen disponibles para sus tests.

### 6.2 Spring sólo ensambla y adapta

Los casos de uso no reciben `@Service` ni `@Component`. Una clase `@Configuration` del backend los
declara mediante métodos `@Bean`, usando sus interfaces como tipos públicos.

### 6.3 Persistencia en memoria

Los `InMemory*Repository`, `InMemoryAuditLog` y `SequentialIdGenerator` se usan como adaptadores de
producción para esta entrega. Spring crea una instancia compartida de cada uno durante la vida de la
aplicación. Perder los datos al reiniciar es una limitación aceptada.

### 6.4 Puertos como frontera

Los adaptadores web futuros reciben puertos de entrada como dependencias. La configuración Spring
no expone repositorios a controllers y no agrega getters o accesos alternativos al núcleo.

### 6.5 Tiempo e identificadores inyectados

El backend declara `Clock.systemUTC()` como bean y una única instancia de `IdGenerator`. Los tests
pueden reemplazarlos por implementaciones deterministas.

### 6.6 Sin cambios de paquete innecesarios

Mover archivos entre módulos no obliga a cambiar sus paquetes Java. Mantener los paquetes actuales
cuando siguen describiendo correctamente su responsabilidad. Cambiar `infrastructure.*` a
`adapter.out.*` sólo si el equipo decide que el beneficio compensa el ruido y se actualizan imports,
tests y documentación de forma consistente.

## 7. Matriz de movimientos

| Elemento actual | Destino | Observación |
| --- | --- | --- |
| `domain/**` | `core/src/main/java/.../domain/**` | Sin cambios funcionales ni dependencias Spring |
| `application/**` | `core/src/main/java/.../application/**` | Mantiene implementación de puertos de entrada |
| Tests de `domain` y `application` | `core/src/test/java/**` | Mantener paquetes y expectativas |
| `infrastructure.memory/**` | `backend/src/main/java/**` | Adaptadores de salida compartidos como beans |
| `infrastructure.id/**` | `backend/src/main/java/**` | `SequentialIdGenerator` como bean único |
| Tests de repositorios en memoria | `backend/src/test/java/**` | Siguen heredando contract tests del core |
| Contract tests de repositorio | `core/src/test/java/**` | Deben estar disponibles para tests del backend |
| `infrastructure.config/RoboLeagueCompositionRoot` | Ver sección 8 | No convertirlo en service locator |
| `demo/**` y `Main` | Ver sección 9 | Conservar demo repetible sin mezclarla con REST |
| Fixtures compartidos por módulos | Evaluar por consumidor | No crear un módulo común sólo para tests sin necesidad |

Los contract tests abstractos presentan una dependencia de tests desde `backend` hacia código de
test de `core`. Resolverla con una estrategia Maven explícita y pequeña, por ejemplo un `test-jar`
del core, o mover únicamente esos contratos a una ubicación de test compartida justificada. No
duplicar sus clases en ambos módulos.

## 8. Composition root y configuración Spring

El objetivo no es borrar inmediatamente `RoboLeagueCompositionRoot`, sino separar sus usos:

- producción web: configuración Spring dentro de `backend`;
- tests del core y demo manual: composition root explícito si continúa simplificando los fixtures.

La configuración Spring debe declarar, al menos:

- todos los repositorios en memoria;
- `AuditLog`;
- `IdGenerator`;
- `Clock`;
- `ScheduleConflictDetector`;
- `RankingService`;
- `CategoryScoringService`;
- los 22 puertos de entrada actuales con sus interactors.

Evitar componentes duplicados entre component scanning y métodos `@Bean`. Elegir una única forma
explícita de registrar cada colaborador. La preferida para el core es `@Bean`, porque mantiene las
clases libres de Spring.

`AcceptAppealUseCase` debe recibir `RecalculateStandings` por su interfaz, igual que en el
composition root actual. No sustituir esa relación por acceso al contenedor.

## 9. Demo y datos repetibles

La Entrega 2 exige datos de demostración repetibles. Durante el bootstrap:

- conservar `DemoScenario` y sus fixtures mientras sigan compilando y verificando F1, F2 y F3;
- no ejecutar automáticamente la demo cada vez que inicia el backend, porque contaminaría los tests
  y el estado de una ejecución normal;
- si se desea cargarla desde Spring, hacerlo después mediante un perfil explícito de demo;
- mantener `DemoScenarioTest` o una prueba equivalente que demuestre el recorrido existente.

La ubicación final de `demo` se decide por sus dependencias reales: si sólo usa puertos del core
puede ser un adaptador de entrada dentro del backend o un módulo de demo posterior. No introducir un
módulo adicional durante este plan salvo que sea imprescindible para compilar.

## 10. Comprobación HTTP técnica

Agregar una única operación técnica para demostrar que Spring Boot inicia y atiende HTTP. Puede ser
un endpoint de salud propio o el mecanismo estándar elegido por el backend.

Condiciones:

- no representa una funcionalidad del negocio;
- no consulta repositorios;
- no expone información sensible o el grafo de beans;
- tiene una prueba de API que inicia el contexto y comprueba una respuesta exitosa;
- no se usa como excusa para diseñar anticipadamente el contrato funcional.

Si se usa una dependencia adicional como Actuator, justificarla frente a la alternativa de un
controller técnico mínimo. Preferir la opción más pequeña para el alcance de la entrega.

## 11. Secuencia de ejecución

### Paso 1 — Registrar una línea de base

1. Revisar `git status` y preservar cambios del usuario.
2. Ejecutar `mvn verify` antes de mover archivos.
3. Si Maven no está disponible o la línea de base falla, registrar el bloqueo y no atribuir fallos
   posteriores al refactor sin evidencia.
4. Guardar cantidad de tests y resultado de cobertura para comparar al final.

### Paso 2 — Crear el parent Maven

1. Convertir el `pom.xml` raíz en `packaging=pom`.
2. Declarar módulos `core` y `backend`.
3. Centralizar versiones de Java, plugins y dependencias sólo cuando sean compartidas.
4. Mantener una ejecución raíz de `mvn verify` que verifique todos los módulos.

No agregar todavía `frontend` como módulo Maven.

### Paso 3 — Extraer el core

1. Crear `core/pom.xml`.
2. Mover `domain` y `application` con sus tests.
3. Mover fixtures que sólo necesita el core.
4. Configurar JaCoCo para conservar la cobertura exigida sobre el código del core.
5. Ejecutar los tests del core antes de continuar.
6. Comprobar con búsqueda de imports que el core no depende de Spring ni del backend.

### Paso 4 — Crear el backend

1. Crear `backend/pom.xml` con dependencia sobre `core` y Spring Boot Web.
2. Crear `RoboLeagueApplication` con `@SpringBootApplication`.
3. Mover los adaptadores en memoria y de ids.
4. Resolver la reutilización de contract tests sin duplicarlos.
5. Agregar pruebas de adaptadores en el módulo backend.
6. Confirmar que backend compila dependiendo de core y no al revés.

### Paso 5 — Configurar beans

1. Declarar beans ambientales y adaptadores.
2. Declarar servicios de dominio que necesitan puertos.
3. Declarar los interactors y exponerlos por sus interfaces.
4. Agregar un test que levante el contexto completo.
5. Verificar que existe un único bean por puerto, salvo una multiplicidad deliberada y documentada.

### Paso 6 — Conservar demo y composición manual

1. Ubicar `DemoScenario`, `DemoRulebook`, `Main` y su test sin crear dependencias invertidas.
2. Mantener un modo documentado de ejecutar la demo.
3. Evitar que el backend cargue datos automáticamente fuera de un perfil explícito.

### Paso 7 — Agregar la comprobación HTTP

1. Crear la operación técnica mínima.
2. Agregar su prueba HTTP con contexto real.
3. Confirmar que no accede a repositorios ni al core de negocio.

### Paso 8 — Actualizar documentación

1. Actualizar `README.md` con requisitos, comandos raíz, ejecución del backend y demo.
2. Actualizar `DESIGN.md` para documentar el nuevo composition root Spring, la separación física de
   módulos y las alternativas descartadas.
3. Actualizar `AGENTS.md` sólo si cambian rutas o comandos respecto de lo previsto.
4. No convertir este plan en documentación histórica duplicada: una vez ejecutado, las decisiones
   vigentes pertenecen a `DESIGN.md` y `architecture.md`.

### Paso 9 — Verificación final

1. Ejecutar `mvn verify` desde la raíz.
2. Ejecutar el backend y comprobar su arranque.
3. Ejecutar la prueba HTTP técnica.
4. Ejecutar la demo repetible.
5. Confirmar que el árbol no contiene `target/` versionado.
6. Revisar los imports y las dependencias Maven de ambos módulos.

## 12. Criterios de aceptación técnicos

**AC-1 — Build raíz.**
Given un checkout limpio con Java 21 y Maven,
When se ejecuta `mvn verify` desde la raíz,
Then se compilan y verifican `core` y `backend` sin pasos manuales intermedios.

**AC-2 — Core independiente.**
Given el módulo `core`,
When se inspeccionan su `pom.xml` e imports de producción,
Then no contiene dependencias ni imports de Spring, HTTP, Jackson, Jakarta o backend.

**AC-3 — Comportamiento preservado.**
Given la suite existente antes del refactor,
When se ejecuta después de mover el código,
Then conserva sus expectativas y cobertura sin reescribir reglas del dominio.

**AC-4 — Backend ejecutable.**
Given el módulo `backend`,
When se inicia la aplicación Spring Boot,
Then el contexto levanta con todos los puertos de entrada configurados y sin beans ambiguos.

**AC-5 — Estado compartido.**
Given dos invocaciones a casos de uso que usan el mismo puerto de repositorio,
When se ejecutan dentro de la misma aplicación,
Then observan la misma instancia de adaptador en memoria.

**AC-6 — Contract tests preservados.**
Given los repositorios en memoria reubicados,
When se ejecutan sus pruebas,
Then siguen cumpliendo `RunResultRepositoryContractTest` y
`StandingsRepositoryContractTest`.

**AC-7 — HTTP disponible.**
Given el backend iniciado,
When se invoca la comprobación técnica,
Then responde exitosamente y la prueba atraviesa la infraestructura HTTP real.

**AC-8 — Demo repetible.**
Given el código reestructurado,
When se ejecuta el mecanismo de demo documentado,
Then reproduce el escenario existente sin depender de autenticación o persistencia durable.

**AC-9 — Límites comprobados.**
Given la estructura final,
When se revisan imports y dependencias,
Then `core` no depende de `backend` y ningún adapter web accede a repositorios.

## 13. Riesgos y tratamiento

| Riesgo | Tratamiento |
| --- | --- |
| Perder cobertura al dividir JaCoCo por módulos | Configurar cobertura por módulo y verificar especialmente `core` |
| No poder reutilizar contract tests | Publicar test-jar o elegir una ubicación compartida explícita, sin duplicar |
| Romper fixtures por mover infraestructura | Separar fixtures del core de los que ensamblan adaptadores |
| Crear dos composition roots divergentes | Mantener uno manual para tests/demo y uno Spring para web con los mismos puertos |
| Component scanning registra objetos por accidente | Preferir configuración explícita de beans para interactors y adaptadores |
| Cargar datos demo en ejecuciones normales | Usar ejecución manual o perfil de demo explícito |
| Sobreimplementar la API durante el bootstrap | Limitar HTTP a la comprobación técnica; usar specs para endpoints funcionales |
| Introducir Spring en el core por comodidad | Bloquear imports y dependencias mediante revisión y tests estructurales si se justifican |

## 14. Condición de finalización

El plan está completo únicamente cuando:

- el build raíz verifica todos los módulos;
- `core` sigue siendo Java puro;
- `backend` inicia como aplicación Spring Boot;
- todos los puertos actuales están correctamente ensamblados;
- los repositorios en memoria mantienen sus contratos y estado compartido;
- la comprobación HTTP técnica pasa;
- los tests y la demo existentes siguen funcionando;
- `README.md` y `DESIGN.md` describen la estructura real;
- no se implementó funcionalidad ajena al bootstrap.

Después de completar este plan, la siguiente sesión debe elegir una spec funcional —se recomienda
`SPEC_configurar-evento.md`— y aplicar las skills de backend y frontend correspondientes.
