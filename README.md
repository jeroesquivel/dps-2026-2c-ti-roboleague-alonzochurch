# RoboLeague

RoboLeague modela una plataforma de competencias de robótica con arquitectura hexagonal. El
repositorio separa el núcleo Java puro de una aplicación web Spring Boot preparada para incorporar
los flujos funcionales de `.docs/specs/` en entregas posteriores.

## Requisitos

- JDK 21 o superior
- Maven 3.9 o superior

## Estructura

```text
core/       dominio, puertos e interactors; no depende de Spring ni de HTTP
backend/    Spring Boot, composición, adaptadores en memoria, demo y entrada HTTP técnica
frontend/   directorio reservado; la tecnología aún no fue elegida
```

El `pom.xml` raíz es un agregador Maven. `backend` depende de `core`; el núcleo no conoce al
backend. Los repositorios actuales son adaptadores en memoria y su contenido se pierde al reiniciar.

## Compilar y verificar

Desde la raíz:

```bash
mvn clean compile
mvn test
mvn verify
```

`mvn verify` verifica ambos módulos. El informe de cobertura del núcleo queda en
`core/target/site/jacoco/index.html` y exige 100 % de instrucciones, ramas, líneas, complejidad,
métodos y clases.

Para ejecutar una prueba concreta:

```bash
mvn -pl core -Dtest=ScoringRulesTest test
mvn -pl backend -am -Dtest=RoboLeagueApplicationTest test
```

## Ejecutar el backend

```bash
mvn -pl backend -am package
java -jar backend/target/roboleague-backend-1.0-SNAPSHOT.jar
```

La única operación HTTP del bootstrap es una comprobación técnica:

```text
GET http://localhost:8080/technical/health
200 {"status":"UP"}
```

No expone funcionalidad de negocio ni consulta repositorios.

## Ejecutar la demo repetible

La demo conserva el recorrido completo existente y no se carga al iniciar Spring Boot:

```bash
mvn -pl backend -am package exec:java -DskipTests
```

Usa datos fijos para inscripción, programación, resultados, puntajes, posiciones y apelaciones. Su
comportamiento se verifica en `DemoScenarioTest`.

Las decisiones vigentes están documentadas en [DESIGN.md](DESIGN.md) y la evolución prevista de la
aplicación web en [architecture.md](architecture.md).
