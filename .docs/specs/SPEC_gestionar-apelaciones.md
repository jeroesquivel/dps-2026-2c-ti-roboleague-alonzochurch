# Spec: Presentar y resolver apelaciones

| | |
|---|---|
| **Status** | Draft |
| **Author** | |
| **Date** | 2026-10-12 |

## 1. Context

Un equipo puede reclamar el resultado de una corrida dentro de la ventana definida por el
reglamento fijado en esa corrida. Un revisor acepta el reclamo aplicando una corrección o lo rechaza
con una decisión. Una aceptación debe conservar el original, corregir el resultado y recalcular las
posiciones existentes.

El núcleo expone `SubmitAppeal`, `FindAppeal`, `AcceptAppeal` y `RejectAppeal`. `Appeal` protege sus
transiciones, `RunResult` conserva `CorrectionHistory` y `AcceptAppealUseCase` coordina el recálculo.
La web debe exponer el flujo sin permitir modificar directamente resultados o standings.

## 2. Functional requirements

**FR-1 — Consultar antes de reclamar.** El equipo puede consultar la corrida y su puntaje para
identificar el resultado sobre el que apelará.

**FR-2 — Presentar apelación.** El usuario presenta un reclamo indicando corrida, equipo, texto del
reclamo y actor de auditoría.

**FR-3 — Validaciones de presentación.** Sólo el equipo dueño puede apelar, la corrida debe estar
completa, la ventana correspondiente debe estar abierta y una corrida sólo puede apelarse una vez.

**FR-4 — Consultar estado.** La consulta muestra identificador, corrida, equipo, reclamo, fecha,
estado y, si fue resuelta, decisión, revisor y fundamento.

**FR-5 — Aceptar.** El revisor acepta una apelación indicando fundamento, mediciones corregidas e
incidentes corregidos. La operación valida la corrección contra el desafío histórico.

**FR-6 — Efectos de aceptación.** Una aceptación deja la apelación aceptada, agrega una corrección al
resultado, registra auditoría y recalcula standings si ya existen.

**FR-7 — Rechazar.** El revisor rechaza una apelación con fundamento. El resultado y los standings no
cambian.

**FR-8 — Resolución única.** Una apelación aceptada o rechazada no puede resolverse nuevamente.

**FR-9 — Publicación bloqueada.** Los standings de la categoría no pueden publicarse mientras exista
una apelación pendiente.

**FR-10 — Interfaz mínima.** El frontend permite presentar, consultar, aceptar o rechazar una
apelación y observar la corrección y el recálculo resultantes.

## 3. Constraints (non-functional)

**C-1 — Puertos separados.** Aceptar y rechazar continúan siendo operaciones distintas; la API no
introduce un endpoint con un flag que combine ambas decisiones.

**C-2 — Corrección protegida.** El controller no modifica un `RunResult` ni invoca repositorios. Toda
aceptación pasa por `AcceptAppeal`.

**C-3 — Reglamento histórico.** La validación usa la versión fijada en la corrida, no la versión
activa actual.

**C-4 — DTOs web.** `Appeal`, `AppealDecision`, `ResultCorrection` y `RunResult` no se serializan
directamente.

**C-5 — Consistencia observable.** En los rechazos esperados no deben aparecer cambios parciales en
apelación, resultado, standings o auditoría.

## 4. Acceptance criteria

**AC-1 — Presentación válida.**
Given una corrida completa dentro de la ventana y su equipo dueño,
When se presenta un reclamo válido,
Then la API devuelve un `AppealId` y la consulta muestra estado pendiente.

**AC-2 — Equipo ajeno o fuera de término.**
Given un equipo que no es dueño o una ventana vencida,
When se intenta apelar,
Then la API rechaza la operación y no guarda ni audita una apelación nueva.

**AC-3 — Apelación única por corrida.**
Given una corrida ya apelada, incluso si la primera apelación fue resuelta,
When se presenta otra,
Then la API responde con conflicto.

**AC-4 — Aceptación válida.**
Given una apelación pendiente y una corrección válida,
When se acepta,
Then la apelación queda aceptada, el resultado corregido y la auditoría registra ambos efectos.

**AC-5 — Recálculo automático.**
Given standings existentes para la categoría,
When se acepta una apelación que corrige una corrida,
Then se crea una nueva revisión provisional con el puntaje corregido.

**AC-6 — Aceptación sin standings.**
Given una apelación pendiente antes de generar posiciones,
When se acepta,
Then se corrige el resultado y no se crean standings implícitamente.

**AC-7 — Rechazo.**
Given una apelación pendiente,
When se rechaza con fundamento,
Then queda rechazada y el resultado y los standings permanecen iguales.

**AC-8 — Resolución repetida o corrección inválida.**
Given una apelación resuelta o una corrección que viola el reglamento,
When se intenta aceptar o rechazar,
Then la API devuelve el error correspondiente y no produce efectos parciales.

**AC-9 — Publicación bloqueada.**
Given una apelación pendiente,
When se intenta publicar standings,
Then la API responde con conflicto hasta que la apelación sea resuelta.

## 5. Out of scope

- Adjuntar archivos, videos o evidencia binaria.
- Reabrir apelaciones resueltas.
- Apelar una decisión de apelación.
- Correcciones administrativas fuera del flujo de aceptación.
- Autenticación o permisos de equipos y revisores.

## 6. Open questions

- Definir si las mediciones corregidas deben enviarse completas o como diferencias respecto del
  resultado actual; el puerto actual requiere el conjunto completo.
- Definir cómo obtiene el frontend los valores actuales necesarios para preparar una corrección sin
  exponer detalles internos innecesarios.

