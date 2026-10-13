# Spec: Consultar la auditoría de una operación

| | |
|---|---|
| **Status** | Draft |
| **Author** | |
| **Date** | 2026-10-12 |

## 1. Context

La entrega exige conservar valores originales y todas las modificaciones. El núcleo registra
`AuditEvent` para las operaciones relevantes y ofrece `FindAuditTrail`, que busca eventos asociados
a un identificador de dominio. La aplicación puede hacer visible esa traza para comprobar quién
realizó una acción, cuándo ocurrió y qué detalle quedó registrado.

Esta spec separa la consulta de auditoría del flujo que genera los eventos. No convierte la
bitácora en fuente del estado actual ni expone su almacenamiento interno.

## 2. Functional requirements

**FR-1 — Consultar por sujeto.** El usuario puede solicitar la traza de un identificador soportado,
como una competencia, categoría, ronda, corrida o apelación.

**FR-2 — Orden.** La respuesta presenta los eventos en un orden temporal determinista.

**FR-3 — Contenido.** Cada evento muestra instante, acción, sujeto, actor y detalles registrados que
sean adecuados para el contrato público.

**FR-4 — Original y corrección.** Para un resultado corregido, la traza permite distinguir captura
original, corrección, motivo y apelación relacionada cuando esos datos fueron auditados.

**FR-5 — Decisión de apelación.** La traza de una apelación permite observar presentación y
resolución, incluido estado y fundamento registrado.

**FR-6 — Historial vacío.** Consultar un sujeto válido sin eventos produce una lista vacía y no un
error de recurso inexistente, salvo que el contrato decida validar previamente la existencia.

**FR-7 — Interfaz mínima.** El frontend permite ingresar o seleccionar un identificador y presenta
la secuencia de eventos y sus detalles.

## 3. Constraints (non-functional)

**C-1 — Puerto de entrada.** La consulta usa `FindAuditTrail`; el controller no accede a
`InMemoryAuditLog`.

**C-2 — DTO de auditoría.** `AuditEvent`, `AuditAction` y `AuditDetail` no se convierten
automáticamente en el contrato público. El mapper decide explícitamente sus nombres y campos.

**C-3 — Sólo lectura.** La API no permite crear, modificar o eliminar eventos de auditoría.

**C-4 — Sin reconstrucción.** La auditoría complementa al estado actual; el frontend no reconstruye
agregados reproduciendo eventos.

**C-5 — Datos sensibles.** Aunque esta entrega no tiene autenticación, no se exponen stack traces,
objetos Java serializados ni datos que no formen parte de `AuditEvent`.

## 4. Acceptance criteria

**AC-1 — Captura de resultado.**
Given una corrida capturada,
When se consulta su auditoría,
Then la respuesta incluye el evento de captura con instante, actor y detalles registrados.

**AC-2 — Resultado corregido por apelación.**
Given una apelación aceptada que corrigió un resultado,
When se consulta la traza del resultado y de la apelación,
Then se observan presentación, resolución y corrección en el orden correspondiente;
and los detalles permiten distinguir valores originales y actuales cuando fueron auditados.

**AC-3 — Apelación rechazada.**
Given una apelación rechazada,
When se consulta su traza,
Then aparecen presentación y resolución con estado rechazado y fundamento;
and no aparece una corrección de resultado.

**AC-4 — Sin eventos.**
Given un identificador sin eventos asociados,
When se consulta,
Then la API devuelve una colección vacía con respuesta exitosa según el contrato acordado.

**AC-5 — Sólo lectura.**
Given un cliente de la API,
When interactúa con el recurso de auditoría,
Then sólo dispone de operaciones de consulta.

## 5. Out of scope

- Event Sourcing o reconstrucción de agregados desde la bitácora.
- Edición o eliminación de eventos.
- Búsqueda global por fecha, actor o acción.
- Exportación, retención, firma o almacenamiento durable de auditoría.
- Ocultamiento por permisos, dado que no hay autenticación en esta etapa.

## 6. Open questions

Resueltas:

- **Pantalla.** La Entrega 2 incluye una pantalla de auditoría (FR-7), además de la API de consulta.
  `FindAuditTrail` deja de ser sólo de demo/tests.
- **Sujeto.** Un único recurso, `GET /audit?subjectId=RUN-1`. El tipo se deduce del prefijo del
  identificador (`SEASON`, `COMPETITION`, `CATEGORY`, `TEAM`, `ROUND`, `RUN`, `APPEAL`), que es el que
  generan los ids secuenciales. Un identificador ausente o con un prefijo no soportado responde
  `400 INVALID_VALUE`. Una URL por recurso habría repetido el endpoint en cada controller y sumado un
  prefijo al proxy del frontend por cada uno.
- **Sin eventos.** Un identificador bien formado sin eventos, o que no existe, responde `200` con
  `events: []` (FR-6, AC-4). No se valida la existencia del sujeto: hacerlo exigiría que la auditoría
  consultara los repositorios de cada agregado, y la bitácora no es fuente del estado actual (C-4).
- **Orden.** Por instante de ocurrencia; los eventos del mismo instante conservan su orden de registro
  (en una aceptación, la resolución de la apelación precede a la corrección que causa).
