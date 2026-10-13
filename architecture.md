# Arquitectura de la aplicación web

Este documento registra las decisiones preliminares para evolucionar el módulo de dominio de
RoboLeague hacia una aplicación web con API REST y frontend. Complementa a [DESIGN.md](DESIGN.md):
ese documento explica el diseño actual del núcleo, mientras que este describe cómo incorporar
Spring Boot y una interfaz web sin romper la arquitectura hexagonal existente.

## 1. Objetivo

La aplicación completa tendrá tres partes claramente separadas:

1. Un **núcleo** independiente de frameworks, formado por `domain` y `application`.
2. Un **backend Spring Boot**, que expondrá los casos de uso mediante HTTP e implementará los
   puertos de infraestructura.
3. Un **frontend**, que consumirá exclusivamente el contrato HTTP de la API.

La dirección de las dependencias será siempre hacia el núcleo:

```text
Frontend
   |
   | HTTP + JSON
   v
Adaptador REST de Spring Boot
   |
   | puertos de entrada
   v
Application + Domain
   |
   | puertos de salida
   v
Adaptadores en memoria, auditoría e identificadores
```

Spring Boot será la carcasa de ejecución y ensamblado. No será parte del modelo de negocio.

## 2. Estructura de módulos propuesta

Se recomienda convertir el proyecto en un proyecto Maven multimódulo:

```text
roboleague/
|-- pom.xml
|-- core/
|   |-- pom.xml
|   `-- src/
|       |-- main/java/com/dps/roboleague/
|       |   |-- domain/
|       |   `-- application/
|       `-- test/
|-- backend/
|   |-- pom.xml
|   `-- src/main/java/com/dps/roboleague/
|       |-- RoboLeagueApplication.java
|       |-- adapter/
|       |   |-- in/web/
|       |   `-- out/
|       |       `-- memory/
|       `-- config/
`-- frontend/
    |-- package.json
    `-- src/
```

Las dependencias permitidas serán:

```text
backend  ---> core
frontend ---> backend, sólo mediante HTTP
core     -X-> backend, Spring o frontend
```

Separar los módulos permite que Maven haga cumplir el límite arquitectónico. Si inicialmente se
mantiene un único módulo, deberán respetarse los mismos límites mediante paquetes, pero esa opción
ofrece menos protección ante imports accidentales.

## 3. Núcleo independiente de frameworks

El módulo `core` conservará:

- entidades, agregados, value objects y servicios en `domain`;
- puertos de entrada en `domain.port.in`;
- repositorios, `AuditLog` e `IdGenerator` como puertos de salida definidos por el dominio;
- interactors en `application.usecase`.

El núcleo no deberá importar:

- Spring Framework o Spring Boot;
- Spring MVC, Jackson o anotaciones HTTP;
- Spring Data, JPA u otra tecnología de persistencia;
- clases del frontend;
- entidades o DTOs de infraestructura.

Los casos de uso continuarán recibiendo todas sus dependencias por constructor. No es necesario
agregarles `@Service` ni `@Component`: Spring podrá registrarlos desde una clase de configuración.

## 4. Adaptador de entrada REST

La API REST será un adaptador de entrada, ubicado en el backend, por ejemplo bajo
`adapter.in.web`. Estará compuesto por:

- controllers;
- DTOs de request y response;
- mappers entre DTOs y entradas/salidas tipadas del núcleo;
- validaciones propias del formato HTTP;
- manejo global de excepciones;
- configuración web, CORS y documentación del contrato.

Los controllers dependerán de los puertos de entrada:

```java
private final CreateSeason createSeason;
```

No deberán depender de:

```java
CreateSeasonUseCase       // implementación concreta
SeasonRepository          // puerto de salida
InMemorySeasonRepository  // detalle de infraestructura
```

La responsabilidad de un controller será:

1. Recibir HTTP y deserializar JSON.
2. Validar el formato de entrada.
3. Convertir el request DTO a un command o identificador tipado.
4. Invocar un puerto de entrada.
5. Convertir el resultado a un response DTO.
6. Devolver el estado HTTP correspondiente.

Las reglas de negocio no se implementarán en controllers, mappers ni DTOs.

## 5. DTOs y contrato público

Los agregados del dominio no se serializarán directamente como contrato público. La API tendrá
DTOs propios:

```text
JSON
  -> Request DTO
  -> mapper
  -> Command o ID tipado
  -> puerto de entrada
  -> resultado del núcleo
  -> mapper
  -> Response DTO
  -> JSON
```

Esta separación permite:

- cambiar el formato HTTP sin modificar el dominio;
- evolucionar el dominio sin romper automáticamente el frontend;
- decidir explícitamente qué información se expone;
- evitar anotaciones de Jackson dentro del núcleo;
- versionar el contrato público de forma independiente.

Los DTOs deberán contener datos de transporte, no comportamiento ni decisiones del negocio.

Cuando un command requiera un `Actor` para auditoría, el adaptador web podrá construirlo a partir
de un dato recibido en la petición. En esta etapa no se autentica al actor ni se restringe el acceso
a las operaciones. Si más adelante se incorpora autenticación, sólo cambiará la forma en que el
adaptador web obtiene ese dato antes de crear el command; los puertos, casos de uso y reglas del
core conservarán el mismo contrato.

## 6. Diseño de endpoints

Los recursos y URLs se diseñarán a partir de las operaciones de usuario y las pantallas, no como
una traducción automática de las clases Java. Los casos de uso existentes serán la referencia para
determinar qué operaciones puede ofrecer la API.

Propuesta inicial, conservada como registro histórico. El contrato vigente no usa el prefijo `/api`
(las rutas reales son `/seasons`, `/teams`, `/rounds`, `/runs`, `/competitions`, `/appeals` y `/audit`) y
está documentado en el [README](README.md), en las secciones «API: …»:

```text
POST /api/seasons
POST /api/seasons/{seasonId}/competitions

POST /api/competitions/{competitionId}/teams
GET  /api/team-registrations/{teamId}

POST /api/competitions/{competitionId}/rounds
GET  /api/rounds/{roundId}

POST /api/rounds/{roundId}/runs
GET  /api/runs/{runId}
GET  /api/runs/{runId}/score

POST /api/competitions/{competitionId}/categories/{categoryId}/standings
GET  /api/competitions/{competitionId}/categories/{categoryId}/standings
POST /api/competitions/{competitionId}/categories/{categoryId}/standings/publication

POST /api/runs/{runId}/appeals
GET  /api/appeals/{appealId}
POST /api/appeals/{appealId}/acceptance
POST /api/appeals/{appealId}/rejection
```

Estas URLs fueron un punto de partida y no son el contrato vigente: durante la implementación se
contrastaron con el enunciado, los actores y los flujos completos, y algunas cambiaron (por ejemplo,
la captura de resultados quedó bajo `/runs`). Ante cualquier diferencia, valen el README y el código.

## 7. Ensamblado con Spring

Spring reemplazará el ensamblado de producción que actualmente realiza
`RoboLeagueCompositionRoot`, pero el composition root manual puede conservarse para tests y para la
demo del núcleo.

Los interactors se registrarán explícitamente como beans:

```java
@Configuration
class UseCaseConfiguration {

    @Bean
    CreateSeason createSeason(
            SeasonRepository seasons,
            IdGenerator ids,
            AuditLog auditLog,
            Clock clock) {
        return new CreateSeasonUseCase(seasons, ids, auditLog, clock);
    }
}
```

La configuración de Spring podrá conocer tanto las implementaciones como las interfaces. Los
objetos del núcleo sólo conocerán interfaces del dominio y tipos del JDK.

También se declarará `Clock` como bean para mantener el tiempo inyectable y testeable. La misma
regla se aplicará a `IdGenerator` y cualquier dependencia ambiental futura.

## 8. Adaptadores de almacenamiento en memoria

En esta etapa se conservarán los repositorios en memoria existentes. No se incorporarán JPA,
Spring Data ni una base de datos porque la persistencia durable no forma parte del alcance actual y
esas tecnologías todavía no fueron estudiadas por el equipo.

Los repositorios continúan siendo puertos definidos por el dominio, mientras que las clases
`InMemory*Repository` son sus adaptadores de salida:

```text
SeasonRepository                    puerto del dominio
        ^
        | implements
InMemorySeasonRepository            adaptador de salida actual
        |
        v
Map en memoria
```

Spring configurará una única instancia de cada adaptador en memoria para que los casos de uso del
backend compartan el estado durante la ejecución. Al reiniciar la aplicación, los datos se
perderán; esta limitación es conocida y aceptada para el alcance actual.

Los adaptadores deberán seguir cumpliendo los tests de contrato de sus respectivos puertos. El
backend no accederá directamente a sus estructuras internas y los controllers no consultarán
repositorios: toda operación seguirá pasando por un puerto de entrada.

Una futura base de datos sólo requeriría agregar nuevos adaptadores que implementen los mismos
puertos y cambiar su selección en la configuración. Esa eventual evolución no debería modificar
controllers, casos de uso ni reglas del dominio, siempre que los nuevos adaptadores conserven los
contratos existentes. JPA y cualquier diseño asociado quedan explícitamente fuera de esta etapa.

## 9. Atomicidad y concurrencia en esta etapa

Algunos casos de uso producen varios efectos. Aceptar una apelación, por ejemplo, puede guardar una
apelación, corregir un resultado, auditar y recalcular standings. Los adaptadores en memoria no
ofrecen una transacción real que revierta automáticamente todos esos efectos ante un fallo parcial.

Para el alcance actual se conserva el comportamiento y las protecciones ya implementadas por el
núcleo y los repositorios en memoria, incluidos sus controles de conflicto. No se agregará
`@Transactional` ni infraestructura de transacciones sin una persistencia que la justifique.

Esta limitación deberá permanecer documentada. Si en el futuro se incorpora almacenamiento
durable, la transacción deberá envolver el caso de uso completo desde un adaptador o decorador
externo al núcleo; no se introducirán anotaciones transaccionales en entidades o value objects.

## 10. Errores HTTP

Un `@RestControllerAdvice` traducirá las excepciones del dominio sin modificar su definición. El
mapeo inicial esperado es:

| Excepción | Respuesta HTTP inicial |
| --- | --- |
| `InvalidValueException` | `400 Bad Request` o `422 Unprocessable Entity` |
| `RuleViolationException` | `422 Unprocessable Entity` |
| `ConflictException` | `409 Conflict` |
| `NotFoundException` | `404 Not Found` |

El formato de error es un DTO estable (`code` y `message`) y no expone stack traces ni detalles
internos.

Mapeo vigente, implementado en un único `ApiExceptionHandler`: `InvalidValueException` → `400`
(`INVALID_VALUE`); JSON mal formado → `400` (`MALFORMED_REQUEST`); `NotFoundException` → `404`;
conflicto de agenda y `ConflictException` → `409`; `RuleViolationException` → `422`
(`RULE_VIOLATION`); el resto de las excepciones del dominio → `422` (`DOMAIN_ERROR`). Los detalles por
endpoint están en el README.

## 11. Frontend

El frontend será una aplicación independiente y sólo conocerá:

- endpoints;
- métodos y códigos HTTP;
- estructuras JSON;
- reglas necesarias para presentación y experiencia de usuario.

No dependerá de clases Java ni accederá directamente al almacenamiento interno del backend. Puede
repetir validaciones simples para mejorar la experiencia, pero el backend y el dominio seguirán
siendo la autoridad de las reglas.

Se recomienda organizarlo por funcionalidad:

```text
frontend/src/
|-- api/
|   |-- http-client
|   |-- seasons-api
|   |-- competitions-api
|   |-- results-api
|   `-- standings-api
|-- features/
|   |-- seasons/
|   |-- competitions/
|   |-- teams/
|   |-- schedule/
|   |-- results/
|   |-- standings/
|   `-- appeals/
|-- shared/
`-- app/
```

El frontend está implementado con React, Vite y TypeScript, y se prueba con Vitest y Testing
Library (ver [frontend/README.md](frontend/README.md)). En desarrollo, Vite reenvía las rutas de la
API al backend mediante un proxy, por lo que no hace falta configurar CORS. La estructura
recomendada arriba es una guía; la organización vigente por funcionalidad está en `frontend/src`.

## 12. Estrategia de pruebas

Se mantendrán los tests actuales del núcleo, sin Spring. La aplicación completa agregará:

- tests unitarios de mappers y representación HTTP cuando aporten valor;
- tests de controllers con Spring MVC;
- tests del manejo de errores;
- tests de integración de los adaptadores en memoria;
- tests de contrato heredados por cada implementación de repositorio;
- tests de integración del backend;
- tests del frontend;
- algunos recorridos end-to-end de los flujos críticos.

No será necesario levantar Spring para probar reglas de puntaje, elegibilidad, agenda, resultados,
ranking o apelaciones. Esas pruebas seguirán perteneciendo al núcleo.

## 13. Orden de implementación recomendado

La implementación avanzará mediante funcionalidades verticales, evitando crear toda la API o todo
el frontend de una sola vez.

Para cada flujo:

1. Confirmar el requisito y el actor que lo ejecuta.
2. Diseñar el contrato HTTP.
3. Crear request DTO, response DTO y mapper.
4. Crear el controller que invoque el puerto existente.
5. Registrar el caso de uso y sus dependencias como beans.
6. Probar el endpoint con los adaptadores en memoria.
7. Construir una pantalla mínima que consuma el endpoint.
8. Completar pruebas de integración y manejo de errores.

Una primera vertical razonable sería:

```text
crear temporada
   -> consultar temporada o resultado de creación
   -> crear competencia
```

Con ella se valida temprano el recorrido completo:

```text
frontend -> HTTP -> controller -> puerto -> caso de uso -> repositorio
```

## 14. Estado de las decisiones

Decididas durante la implementación:

- tecnología del frontend: React, Vite y TypeScript;
- contrato de endpoints y DTOs: documentado en el [README](README.md);
- formato uniforme de errores: `code` y `message`, con el mapeo de la sección 10;
- CORS: no se configura; en desarrollo el frontend usa un proxy de Vite.

Sin resolver, por no responder todavía a un requisito del enunciado:

- versionado de la API;
- forma de desplegar frontend y backend (hoy se ejecutan localmente);
- OpenAPI y generación de clientes.

Estas decisiones no deben anticiparse con implementaciones "por si acaso". Cada adaptador, endpoint
o dependencia nueva deberá responder a un requisito o consumidor concreto.

## 15. Reglas arquitectónicas resultantes

1. `core` compila y se prueba sin Spring.
2. Spring ensambla los componentes; el dominio toma las decisiones.
3. Los controllers invocan puertos de entrada, nunca repositorios.
4. Los DTOs HTTP son distintos de los agregados del dominio.
5. Los repositorios en memoria implementan puertos definidos por el dominio.
6. La elección de almacenamiento en memoria es un detalle reemplazable del adaptador de salida.
7. El frontend sólo se integra mediante el contrato HTTP.
8. No se incorpora autenticación ni autorización en esta etapa.
9. No se incorpora infraestructura transaccional mientras el almacenamiento sea en memoria.
10. Una nueva tecnología se incorpora como adaptador, sin trasladar sus conceptos al núcleo.
11. Se implementan verticales completas y verificables, guiadas por requisitos concretos.
12. Todo cambio que altere una decisión arquitectónica se documenta también en `DESIGN.md`.
