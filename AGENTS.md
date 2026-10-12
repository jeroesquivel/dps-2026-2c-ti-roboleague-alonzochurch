# AGENTS.md

Guía mínima para agentes que trabajan en este repositorio. Para el contexto funcional ver
[README.md](README.md), para las decisiones vigentes ver [DESIGN.md](DESIGN.md) y para la evolución
hacia la aplicación web ver [architecture.md](architecture.md).

## Proyecto

RoboLeague modela una plataforma de competencias de robótica con arquitectura hexagonal. El núcleo
está formado por `domain` y `application`; Spring Boot y el frontend se incorporan como adaptadores
externos. Los repositorios en memoria son los adaptadores de salida actuales.

Antes de implementar un flujo funcional, leer su spec en `.docs/specs/`. No agregar funcionalidad
fuera de la spec ni resolver una pregunta abierta mediante una suposición que cambie el contrato.

## Comandos

```bash
mvn clean compile                       # compilar
mvn test                                # correr todos los tests
mvn -pl core -Dtest=ScoringRulesTest test        # una clase del core
mvn -pl core -Dtest=ScoringRulesTest#method test # un método del core
mvn verify                              # tests + controles de cobertura
mvn -pl backend -am package exec:java -DskipTests # ejecutar la demo actual
```

Antes de terminar un cambio, `mvn verify` debe pasar. JaCoCo exige 100 % de instrucciones, ramas,
líneas, complejidad, métodos y clases; todo código nuevo necesita pruebas para sus caminos exitosos
y de error. El informe se genera en `core/target/site/jacoco/index.html`.

## Límites arquitectónicos

- `domain` sólo depende del dominio y del JDK; no importa `application`, infraestructura ni
  frameworks.
- `application` depende de puertos y tipos del dominio, nunca de adaptadores concretos.
- El núcleo es dueño de sus puertos. Los adaptadores dependen del núcleo, no al revés.
- Los controllers invocan puertos de entrada y nunca repositorios ni interactors concretos.
- Spring, HTTP, JSON y conceptos del frontend no aparecen dentro del núcleo.
- Los adaptadores traducen; no contienen reglas de negocio ni se conocen entre sí.
- Los casos de uso coordinan: cargan, delegan en el dominio, persisten y auditan. Las decisiones de
  negocio pertenecen al dominio.
- Los repositorios, `AuditLog`, `IdGenerator` y `Clock` se inyectan por constructor. No usar tiempo,
  identificadores aleatorios, singletons ni estado estático mutable como dependencias ocultas.
- El composition root o la configuración Spring sólo exponen puertos de entrada hacia los
  adaptadores de entrada.
- No agregar métodos a interfaces sin un cliente concreto en código de producción.

## Skills

Leer únicamente las skills relacionadas con el cambio solicitado:

- Cambios o extensiones del núcleo:
  [`.agents/skills/core/evolve-core/SKILL.md`](.agents/skills/core/evolve-core/SKILL.md).
- Endpoints y adaptación HTTP con Spring Boot:
  [`.agents/skills/backend/add-rest-endpoint/SKILL.md`](.agents/skills/backend/add-rest-endpoint/SKILL.md).
- Flujos de interfaz web:
  [`.agents/skills/frontend/implement-flow/SKILL.md`](.agents/skills/frontend/implement-flow/SKILL.md).

Si un cambio cruza más de un área, aplicar las skills correspondientes sin trasladar sus decisiones
de una capa a otra.

## Especificaciones

Una spec representa un flujo funcional y contiene contexto, requisitos, restricciones específicas,
criterios de aceptación, fuera de alcance y preguntas abiertas. Usar
[`.docs/specs/SPEC_TEMPLATE.md`](.docs/specs/SPEC_TEMPLATE.md) como base.

La spec dice **qué comportamiento** debe conseguirse. Las skills explican **cómo trabajar** en cada
parte del proyecto. No repetir en una spec las reglas generales de este archivo o de
`architecture.md`.

## Convenciones generales

- Identificadores, mensajes de excepción y nombres de tests en inglés; documentación en español.
- Mantener entradas e identificadores tipados al atravesar el núcleo.
- Preservar los cambios existentes del usuario y evitar modificaciones ajenas al pedido.
- Actualizar `DESIGN.md` cuando cambie una decisión de diseño, una regla se mueva de capa o se
  descarte una alternativa relevante.
- Las correcciones de entregas se registran en `.docs/correcciones-entrega-*.md` con su estado y la
  evidencia de resolución.
- Usar mensajes de commit cortos con prefijo en mayúsculas, por ejemplo `ADD run result endpoint` o
  `FIX standings publication`. No commitear `target/`.
