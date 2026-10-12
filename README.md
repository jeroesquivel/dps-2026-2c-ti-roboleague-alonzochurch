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

La comprobación técnica no consulta repositorios:

```text
GET http://localhost:8080/technical/health
200 {"status":"UP"}
```

## API: equipos

Spec: [`.docs/specs/SPEC_registrar-equipo.md`](.docs/specs/SPEC_registrar-equipo.md). El controller
sólo invoca los puertos `RegisterTeam` y `FindTeamRegistration`; la elegibilidad la decide el
reglamento activo de la competencia.

### `POST /teams` — registrar un equipo

```json
{
  "competitionId": "competition-1",
  "categoryId": "category-1",
  "teamName": "Rescue Crew",
  "members": [
    {"fullName": "Ada", "birthDate": "2010-05-20", "role": "COMPETITOR"},
    {"fullName": "Grace", "birthDate": "1988-02-10", "role": "COACH"}
  ],
  "robot": {
    "name": "Rescuer", "robotClass": "RESCUE_BOT", "weightKg": 2.4,
    "dimensions": {"lengthMm": 180, "widthMm": 180, "heightMm": 150}
  },
  "documents": [{"type": "PARENTAL_CONSENT", "reference": "PC-1"}],
  "actor": "organizer"
}
```

- `role`: `COMPETITOR` o `COACH`. `type`: `PARENTAL_CONSENT`, `INSURANCE`, `TECHNICAL_SHEET` o
  `SAFETY_CHECK`. `birthDate` usa formato ISO (`yyyy-MM-dd`).
- `documents` es opcional: si falta se toma como lista vacía y la inscripción queda rechazada por los
  documentos requeridos que el reglamento exija.
- `actor` es obligatorio y se registra en la auditoría; esta etapa no tiene autenticación.

Respuesta `201 Created` con `Location: /teams/{teamId}`, tanto si el equipo queda aceptado como si
queda rechazado (en ambos casos la inscripción existe y puede consultarse):

```json
{
  "teamId": "team-1",
  "status": "REJECTED",
  "violations": [
    {"code": "REQUIRED_DOCUMENTS", "reason": "missing document TECHNICAL_SHEET"}
  ]
}
```

`status` es `ACCEPTED` o `REJECTED`. `violations` enumera **todas** las violaciones detectadas. Cada
una lleva el `code` estable de su regla (`AGE_RANGE`, `TEAM_COMPOSITION`, `ROBOT_CLASS`,
`ROBOT_SPECIFICATION`, `REQUIRED_DOCUMENTS`) y una descripción en `reason`; el texto de `reason` no es
estable y no debe interpretarse.

### `GET /teams/{teamId}` — consultar una inscripción

`200` con `teamId`, `competitionId`, `categoryId`, `name`, `status`, `members` (con `memberId`,
`fullName`, `birthDate`, `role`), `robot`, `documentTypes` (sólo los tipos presentados, no sus
referencias) y `violations` con el mismo formato que arriba.

### Errores

Todos los errores tienen la forma `{"code": "...", "message": "..."}`. El manejador compartido
(`ApiExceptionHandler`) traduce la jerarquía del dominio:

| Excepción del dominio | HTTP | `code` |
| --- | --- | --- |
| `InvalidValueException` (incluye campos ausentes o con formato inválido) | 400 | `INVALID_VALUE` |
| Cuerpo que no es JSON válido | 400 | `MALFORMED_REQUEST` |
| `NotFoundException` (competencia, categoría ajena o inscripción inexistente) | 404 | `NOT_FOUND` |
| `ConflictException` | 409 | `CONFLICT` |
| `RuleViolationException` | 422 | `RULE_VIOLATION` |
| Otra `DomainException` | 422 | `DOMAIN_ERROR` |

## Ejecutar el frontend

El frontend (React + Vite) vive en [`frontend/`](frontend/README.md). Con el backend en
`localhost:8080`:

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173
npm test
```

En desarrollo Vite reenvía `/teams` al backend mediante un proxy, así que no hace falta CORS.

## Ejecutar la demo repetible

La demo conserva el recorrido completo existente y no se carga al iniciar Spring Boot:

```bash
mvn -pl backend -am package exec:java -DskipTests
```

Usa datos fijos para inscripción, programación, resultados, puntajes, posiciones y apelaciones. Su
comportamiento se verifica en `DemoScenarioTest`.

Las decisiones vigentes están documentadas en [DESIGN.md](DESIGN.md) y la evolución prevista de la
aplicación web en [architecture.md](architecture.md).
