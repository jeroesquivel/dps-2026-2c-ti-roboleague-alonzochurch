# Spec: Consultar un puntaje explicable

| | |
|---|---|
| **Status** | Draft |
| **Author** | |
| **Date** | 2026-10-12 |

## 1. Context

Equipos, jueces y organizadores necesitan conocer no sólo el total de una corrida, sino cómo se
obtuvo. El desglose incluye contribuciones de reglas, bonificaciones, penalizaciones, eventuales
recortes por bonus cap y, para desafíos mixtos, separación por fuente.

El núcleo expone `CalculateRunScore` y `FindRunResult`. `ScoreBreakdown`, `ScoreContribution` y
`SourcedScore` contienen la explicación. El tope global y el desglose mixto están definidos en
`SPEC_bonus_sum_global_limit.md` y `SPEC_mixed_challenge.md`. La API proyecta esa información a un
contrato estable sin recalcular nada en la web.

## 2. Functional requirements

**FR-1 — Seleccionar corrida.** El usuario consulta el puntaje mediante un `RunId` existente.

**FR-2 — Total.** La respuesta informa el total de puntos de la corrida.

**FR-3 — Contribuciones.** La respuesta enumera cada contribución con código de regla, naturaleza,
puntos y explicación, incluidas las que aportan cero cuando forman parte del desglose.

**FR-4 — Penalizaciones.** Las penalizaciones aparecen como contribuciones identificables y con
signo coherente con su naturaleza.

**FR-5 — Bonus cap.** Si el desafío limita la suma global de bonificaciones, la respuesta muestra
las bonificaciones obtenidas, el límite y el recorte aplicado según
`SPEC_bonus_sum_global_limit.md`.

**FR-6 — Desglose por fuente.** Para una corrida mixta, la respuesta agrupa las contribuciones de la
fuente automática y del panel de jueces, además de cualquier ajuste global.

**FR-7 — Versión correcta.** El cálculo usa exactamente la versión del reglamento fijada en la
corrida y la respuesta la identifica.

**FR-8 — Corrida pendiente.** Una corrida mixta incompleta no produce puntaje parcial. La API informa
que sigue pendiente y qué fuentes faltan.

**FR-9 — Correcciones.** Una corrida corregida se calcula con sus valores actuales, conservando el
original para el flujo de auditoría.

**FR-10 — Interfaz mínima.** El frontend permite consultar una corrida y presentar total,
contribuciones, penalizaciones, bonus cap y fuentes de manera legible.

## 3. Constraints (non-functional)

**C-1 — Cálculo único.** La API y el frontend no implementan fórmulas; usan exclusivamente
`CalculateRunScore`.

**C-2 — DTO explicable.** No se serializan directamente `ScoreBreakdown`, `ScoreContribution` o
`SourcedScore`. El DTO conserva la información necesaria para explicar el total.

**C-3 — Consistencia.** La suma de las contribuciones y ajustes expuestos coincide con el total
informado por el dominio.

**C-4 — F2 y F3 intactas.** Esta spec no altera las decisiones de las specs existentes; sólo define
su representación observable en la aplicación completa.

**C-5 — Pruebas de API.** Se cubren puntaje normal, penalización, bonus cap, desafío mixto completo,
corrida pendiente y recurso inexistente.

## 4. Acceptance criteria

**AC-1 — Puntaje simple explicable.**
Given una corrida completa válida,
When se consulta su puntaje,
Then la respuesta contiene total, versión y todas las contribuciones;
and la suma explicada coincide con el total.

**AC-2 — Penalización visible.**
Given una corrida con un incidente definido por el reglamento,
When se consulta su puntaje,
Then el desglose incluye la deducción y su explicación.

**AC-3 — Bonus cap visible.**
Given una corrida cuyas bonificaciones superan el tope global,
When se consulta su puntaje,
Then aparecen las bonificaciones obtenidas, el tope y el recorte resultante;
and el total respeta `SPEC_bonus_sum_global_limit.md`.

**AC-4 — Fuentes separadas.**
Given una corrida mixta completa,
When se consulta su puntaje,
Then las contribuciones se presentan separadas por fuente y recomponen el total.

**AC-5 — Corrida pendiente.**
Given una corrida mixta que espera una fuente,
When se solicita su puntaje,
Then la API no devuelve un total parcial y responde con el estado y las fuentes faltantes.

**AC-6 — Reglamento histórico.**
Given una corrida capturada con una versión anterior y una versión nueva activa,
When se consulta su puntaje,
Then el cálculo y la respuesta usan la versión fijada en la corrida.

**AC-7 — Corrida inexistente.**
Given un `RunId` desconocido,
When se consulta su puntaje,
Then la API responde como recurso no encontrado.

## 5. Out of scope

- Puntaje tentativo de corridas pendientes.
- Reimplementar fórmulas en JavaScript.
- Comparar dos corridas en una misma respuesta.
- Exportar desgloses a PDF o planilla.
- Explicar la selección de mejores rondas, cubierta por posiciones.

## 6. Open questions

Resueltas:

- **Corrida pendiente.** `GET /runs/{runId}/score` responde `200` con `completionStatus` `PENDING`,
  `missingSources` (`AUTOMATIC` y/o `JUDGES`) y `total`, `contributions`, `bonusCap` y `bySource` en
  `null`. La corrida existe y la consulta es válida: es un estado, no un error, y así también lo
  informa la tabla de posiciones. `409` y `422` quedan para operaciones que modifican y chocan con el
  estado; además su cuerpo de error no tiene un campo estructurado para las fuentes faltantes.
  `FindRunResult` informa el estado de completitud y sólo una corrida completa se pasa a
  `CalculateRunScore`.
- **Tope de bonificaciones.** `CalculateRunScore.RunScore` incorpora `Optional<BonusCapOutcome>`
  (obtenidas, tope y recorte), calculado por el desafío histórico con la misma cuenta que la
  contribución `BONUS_CAP`. La API lo expone como `bonusCap {obtained, cap, trimmed, applied}`
  (`null` si el desafío no tiene tope) y la contribución `BONUS_CAP` sigue apareciendo en la lista.
  Así el tope y el recorte no dependen de interpretar el texto de una explicación, que no es parte
  del contrato.
- **Nombres públicos.** Se usan los del dominio: `EARNED`, `BONUS`, `PENALTY` y `AUTOMATIC`,
  `JUDGES`. El frontend los traduce a español en sus etiquetas.
