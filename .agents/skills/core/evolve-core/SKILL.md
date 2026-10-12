---
name: evolve-core
description: Modifica o extiende el dominio y los casos de uso de RoboLeague preservando sus invariantes, contratos, arquitectura hexagonal y cobertura. Usar para cambios en domain o application; no para trabajo limitado a REST o frontend.
---

# Evolucionar el core

Aplica esta skill cuando una spec requiere cambiar `domain` o `application`. Antes de actuar, leer
la spec del flujo, las secciones relevantes de `DESIGN.md` y los tests del comportamiento afectado.
No cambiar el core sólo para facilitar JSON, HTTP o presentación.

## Ubicar la decisión

- Una regla sobre el estado de un agregado pertenece al agregado.
- Una invariante de un conjunto pertenece a una colección con nombre propio.
- Una fórmula, desempate, elegibilidad o agregación nueva se incorpora mediante el punto de
  extensión existente (`ScoringRule`, `TiebreakRule`, `EligibilityRule`, `AttemptAggregation`).
- Un caso de uso sólo carga, delega, persiste y audita. Si empieza a decidir con condiciones de
  negocio, mover esa decisión al dominio.
- Crear un puerto nuevo sólo cuando existe un cliente concreto que necesita esa operación.

Mantener un caso de uso por operación. No usar flags o modos para combinar comportamientos
independientes; preferir puertos separados como `AcceptAppeal` y `RejectAppeal`.

## Modelado

- Representar valores con significado mediante value objects tipados, normalmente records que
  validan su invariante y ofrecen una fábrica `of(...)`.
- Mantener los agregados inmutables: campos finales y transiciones que devuelven una nueva
  instancia. El constructor público crea el estado inicial; los privados reconstituyen estados
  posteriores.
- Usar colecciones con nombre cuando el conjunto posee una regla propia.
- Usar comportamiento por constante en enums en lugar de condicionales que enumeren constantes.
- No usar `instanceof`, `switch` con `default` o cadenas de `if` para reemplazar polimorfismo en un
  punto de extensión.
- No implementar contratos parcialmente ni usar `UnsupportedOperationException` para indicar que
  una operación “no aplica”.

## Excepciones

| Excepción | Uso |
| --- | --- |
| `InvalidValueException` | Un valor o configuración viola su propia invariante |
| `RuleViolationException` | Datos bien formados violan una regla de negocio |
| `ConflictException` | La operación choca con el estado actual |
| `NotFoundException` | No existe el elemento referenciado |

## Contratos especiales

Una `ScoringRule` siempre devuelve contribuciones explicables, incluso cuando aporta cero, declara
sus `referencedMetrics()` y conserva un signo compatible con su `ContributionKind`. Toda regla nueva
se agrega a `ScoringRulesTest.everyRule`.

Un adaptador de repositorio debe cumplir el `*RepositoryContractTest` de su puerto. No agregar CRUD
genérico ni consultas sin consumidor para uniformar repositorios artificialmente.

## Pruebas y finalización

- Los tests de dominio construyen sus objetos directamente.
- Los tests de casos de uso usan `support/TestEdition` cuando necesitan el módulo integrado en
  memoria; para controlar el tiempo usan `support/AdjustableClock`.
- No incorporar un framework de mocking. Si un doble pequeño es necesario, escribirlo a mano.
- Cubrir éxito, rechazos esperados y ausencia de persistencia o auditoría cuando un rechazo deba
  dejar el sistema intacto.
- Ejecutar `mvn verify`.
- Actualizar `DESIGN.md` cuando el cambio introduzca o mueva una decisión de diseño, indicando
  patrón o principio, ubicación, motivo y alternativas descartadas.
