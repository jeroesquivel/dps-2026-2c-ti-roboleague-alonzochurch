---
name: add-graphql-resolver
description: Agrega o modifica consultas y resolvers GraphQL de RoboLeague mediante puertos de entrada, tipos propios y pruebas del contrato. No usar para mutaciones ni para cambiar reglas del core.
---

# Agregar un resolver GraphQL

Implementa una consulta definida por `SPEC_api-graphql.md` sin trasladar reglas de negocio al
adaptador. Antes de cambiar código, leer `AGENTS.md`, `architecture.md`, la spec, el fragmento vigente
de `schema.graphqls`, los puertos involucrados y sus pruebas.

## Recorrido

```text
query GraphQL
-> argumento o tipo padre
-> identificador o command tipado
-> puerto de entrada
-> resultado del core
-> mapper y record GraphQL
-> selección pedida por el cliente
```

Definir primero el contrato en `backend/src/main/resources/graphql/schema.graphqls`. Un resolver
depende de interfaces `port.in`, nunca de un `*UseCase`, repositorio, configuración o infraestructura.
Mantener tipos y mappers en `adapter.in.graphql`; no reutilizar DTOs ni mappers REST y no devolver
agregados directamente.

Crear o ampliar un puerto sólo cuando el campo tenga un consumidor concreto. Si eso modifica el
core, aplicar también `evolve-core` y cubrir el repositorio en memoria y JPA con sus tests de contrato.
Registrar el caso de uso como bean en la configuración Spring.

## Relaciones, errores y escalares

- Usar `@QueryMapping` para raíces y `@SchemaMapping` para relaciones del tipo padre.
- Evaluar N+1 en toda relación a colección o entidad repetida. Cuando corresponda, registrar un
  `DataLoader` que delegue en un puerto de lote y cuya caché dure sólo la petición; probar el número
  de invocaciones con un decorador contador.
- Traducir excepciones mediante `DomainErrorResolver`; no capturarlas en cada resolver.
- Exponer puntos con el escalar `Points` y `BigDecimal`, sin convertir a `Float` o `double`.
- Una corrida pendiente se proyecta desde `RunCompletion`: no se calcula un puntaje parcial.

## Pruebas y finalización

- Probar por HTTP con `HttpGraphQlTester`, la configuración real y adaptadores reproducibles.
- Cubrir la selección exitosa, recorrido anidado, entidad ausente y valores inválidos cuando
  correspondan; verificar datos parciales y `path` en errores de campos anidados.
- Para un DataLoader, demostrar lote por nivel y que la caché no sobrevive a otra petición.
- Mantener `BackendBoundariesTest` verde y no modificar el contrato REST.
- Actualizar README y la comparación GraphQL/REST si cambia el contrato público o su costo.
- Ejecutar `mvn verify`.
