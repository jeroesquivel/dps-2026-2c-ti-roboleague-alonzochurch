# Spec: Capturar y consultar resultados

| | |
|---|---|
| **Status** | Implemented |
| **Author** | |
| **Date** | 2026-10-12 |

## 1. Context

Después de disputar un heat se registran las mediciones, objetivos, evaluaciones e incidentes de un
intento. Los desafíos normales reciben todo en una operación; los mixtos reciben mediciones
automáticas y evaluaciones del panel por separado y permanecen pendientes hasta completar ambas
fuentes.

El núcleo ofrece `CaptureRunResult`, `RegisterAutomaticMeasurements`,
`RegisterPanelEvaluations` y `FindRunResult`. El comportamiento mixto está especificado en
`SPEC_mixed_challenge.md`. Esta spec describe cómo se ejecuta y observa ese comportamiento desde la
aplicación web, sin redefinir sus reglas.

## 2. Functional requirements

**FR-1 — Captura completa.** Para un desafío no mixto, el usuario registra ronda, equipo, número de
intento, mediciones, evaluaciones e incidentes en una sola operación.

**FR-2 — Fuente automática.** Para un desafío mixto, el usuario puede registrar las mediciones
automáticas de un intento.

**FR-3 — Fuente de jueces.** Para un desafío mixto, el usuario puede registrar evaluaciones del panel
e incidentes para el mismo intento.

**FR-4 — Orden independiente.** Las dos fuentes mixtas pueden llegar en cualquier orden. La primera
crea una corrida pendiente y la segunda completa esa misma corrida.

**FR-5 — Identidad estable.** Las dos recepciones del mismo equipo, ronda e intento devuelven o
permiten recuperar el mismo `RunId`; no crean dos corridas independientes.

**FR-6 — Estado visible.** La consulta muestra si la corrida está completa o pendiente, qué fuentes
se recibieron, cuáles faltan y quién y cuándo registró cada fuente.

**FR-7 — Datos observables.** La consulta muestra identificadores relevantes, intento, versión del
reglamento, mediciones actuales, evaluaciones, incidentes y estado de correcciones sin exponer el
agregado directamente.

**FR-8 — Rechazos.** Se rechazan intentos fuera de límite, métricas o penalizaciones desconocidas,
paneles inválidos, equipos ajenos a la ronda, fuentes duplicadas y el modo de captura incorrecto.

**FR-9 — Cierre de resultados.** Una vez publicados standings finales, una captura ordinaria no
puede modificar los resultados de la categoría; las correcciones posteriores pasan por apelaciones.

**FR-10 — Interfaz mínima.** El frontend ofrece captura normal y captura separada por fuente,
mostrando el estado pendiente o completo después de cada operación.

## 3. Constraints (non-functional)

**C-1 — F3 intacta.** Se conserva íntegramente `SPEC_mixed_challenge.md`; la web sólo adapta sus
puertos y resultados.

**C-2 — DTOs de métricas.** El contrato representa métricas por claves y valores definidos, sin
serializar `MeasurementSet`, `JudgeEvaluations` o `RunResult` directamente.

**C-3 — Sin puntaje parcial.** La respuesta de captura puede mostrar completitud, pero no inventa un
puntaje para una corrida pendiente.

**C-4 — Concurrencia del adaptador.** El repositorio en memoria conserva las garantías de unicidad y
no pérdida de fuentes fijadas por `RunResultRepositoryContractTest`.

**C-5 — Pruebas.** Las pruebas HTTP cubren ambas órdenes de llegada, captura simple, duplicados,
estado pendiente y estado completo.

## 4. Acceptance criteria

**AC-1 — Captura simple.**
Given una ronda de un desafío no mixto y un equipo asignado,
When se envía un intento válido completo,
Then la API devuelve un `RunId`;
and su consulta muestra estado completo y la versión del reglamento fijada.

**AC-2 — Automática primero.**
Given una ronda de desafío mixto,
When se registran primero las mediciones automáticas,
Then se crea una corrida pendiente que informa que falta la fuente de jueces.

**AC-3 — Jueces primero.**
Given una ronda de desafío mixto,
When se registra primero la evaluación del panel,
Then se crea una corrida pendiente que informa que falta la fuente automática.

**AC-4 — Completar corrida.**
Given una corrida mixta pendiente con una fuente recibida,
When se registra la fuente restante,
Then la misma corrida pasa a completa y conserva ambos recibos.

**AC-5 — Fuente duplicada.**
Given una corrida que ya recibió una fuente,
When se intenta registrar esa fuente nuevamente,
Then la API responde con conflicto y conserva la recepción original.

**AC-6 — Intento duplicado o fuera de límite.**
Given un intento ya usado o superior al máximo del desafío,
When se captura otro resultado con ese intento,
Then la API rechaza la operación y no crea otra corrida.

**AC-7 — Datos inválidos.**
Given una métrica, incidente o evaluación que no cumple el reglamento fijado,
When se registra,
Then la API devuelve el error de negocio y no altera el resultado existente.

**AC-8 — Categoría cerrada.**
Given standings finales para la categoría,
When se intenta una captura ordinaria,
Then la API responde con conflicto y no guarda el resultado.

## 5. Out of scope

- Modificar o eliminar capturas desde este flujo.
- Carga masiva de resultados.
- Recepción automática desde hardware real o mensajería.
- Actualización en tiempo real del frontend.
- Cálculo del puntaje, cubierto por `SPEC_consultar-puntaje-explicable.md`.

## 6. Open questions

Resueltas:

- **URLs.** `POST /runs` (captura completa), `POST /runs/automatic-measurements` y
  `POST /runs/panel-evaluations` (fuentes mixtas), y `GET /runs/{runId}` (consulta, compartida con
  apelaciones y puntaje). Sin prefijo `/api`, igual que el resto de la API. Ronda, equipo, intento y
  actor van en el cuerpo, porque son los datos del comando del núcleo.
- **Respuesta.** Todas las operaciones devuelven el mismo `RunResponse` que la consulta, ampliado con
  `evaluations`; no hay una segunda representación de la corrida. La captura completa y la primera fuente
  responden `201` con `Location: /runs/{runId}`; la fuente que completa una corrida existente responde
  `200` con el mismo `runId`.
- **Recibos.** Se exponen `source`, `actor` y `receivedAt` por fuente recibida (es todo lo que contiene
  un recibo) y las fuentes que faltan en `missingSources`. No se publica nada interno del agregado.
- **Errores.** El formato común `{code, message}`: `409` para fuente repetida, intento ya capturado y
  categoría cerrada; `422` para intento fuera de límite, métricas o penalidades desconocidas, paneles
  inválidos, equipo ajeno a la ronda y modo de captura incorrecto; `400` para campos ausentes o inválidos
  (nunca `500`).
