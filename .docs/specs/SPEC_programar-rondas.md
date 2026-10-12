# Spec: Programar y consultar rondas

| | |
|---|---|
| **Status** | Draft |
| **Author** | |
| **Date** | 2026-10-12 |

## 1. Context

Una competencia configurada necesita rondas con turnos concretos para que los equipos participen.
Cada ronda pertenece a una categoría y desafío, fija la versión activa del reglamento y contiene
heats con equipo, pista, horario y panel de jueces.

El núcleo expone `ScheduleRound` y `FindRound`. `CompetitionSchedule`, `Round` y
`ScheduleConflictDetector` protegen ordinales, límites de rondas, elegibilidad de equipos y
conflictos de equipo, pista o juez. La API debe limitarse a traducir esos resultados.

## 2. Functional requirements

**FR-1 — Crear ronda.** El usuario puede programar una ronda indicando competencia, categoría,
desafío, ordinal y uno o más heats.

**FR-2 — Configurar heat.** Cada heat indica equipo, pista, inicio, duración y jueces asignados.

**FR-3 — Reglamento fijado.** La ronda conserva la versión activa del reglamento al momento de
programarse.

**FR-4 — Equipos habilitados.** Sólo se programan equipos registrados y habilitados para la
competencia y categoría de la ronda.

**FR-5 — Conflictos.** La operación rechaza ordinales repetidos, horarios fuera del evento,
superposiciones de equipo, pista o juez y rondas que superen M cuando el desafío usa mejores N de M.

**FR-6 — Atomicidad observable.** Si algún heat es inválido, la ronda completa se rechaza y no queda
una ronda parcialmente programada.

**FR-7 — Consultar ronda.** El usuario puede consultar la ronda y ver desafío, categoría, ordinal,
versión del reglamento y detalle de sus heats.

**FR-8 — Interfaz mínima.** El frontend permite agregar heats, enviar la programación y mostrar la
ronda o el conflicto informado.

## 3. Constraints (non-functional)

**C-1 — Reglas existentes.** Los controllers no reproducen la detección de conflictos ni las reglas
de `CompetitionSchedule`.

**C-2 — Puertos.** La web usa `ScheduleRound` y `FindRound`; no consulta `RoundRepository` ni
repositorios de equipos.

**C-3 — Tiempo explícito.** Horarios y duración usan un formato HTTP no ambiguo y se convierten a los
tipos del dominio sin aplicar la zona horaria del servidor implícitamente.

**C-4 — Respuesta web.** `Round` y `Heat` se proyectan a DTOs; no se serializan como contrato público.

**C-5 — Pruebas.** Las pruebas de API ejercitan programación exitosa y al menos un conflicto de cada
tipo observable requerido por la spec.

## 4. Acceptance criteria

**AC-1 — Ronda programada.**
Given una competencia, categoría, desafío y equipos aceptados,
When se programa una ronda con heats no superpuestos dentro del período,
Then la API devuelve el `RoundId`;
and la consulta muestra todos los heats y la versión fijada.

**AC-2 — Equipo no habilitado.**
Given un equipo inexistente, rechazado o registrado en otra categoría,
When se lo incluye en un heat,
Then la programación es rechazada y la ronda no se guarda.

**AC-3 — Conflicto de recursos.**
Given dos heats superpuestos que comparten equipo, pista o juez,
When se programa la ronda,
Then la API informa el tipo de conflicto y no guarda parcialmente la ronda.

**AC-4 — Ordinal repetido.**
Given una categoría con una ronda de ordinal 1,
When se intenta programar otra ronda con ordinal 1,
Then la API responde con conflicto.

**AC-5 — Límite mejores N de M.**
Given un desafío configurado con M rondas ya programadas,
When se intenta programar una ronda adicional del mismo desafío y categoría,
Then la API rechaza la operación según `SPEC-mejores-n-de-m-rondas.md`.

**AC-6 — Consulta inexistente.**
Given un `RoundId` desconocido,
When se consulta,
Then la API responde como recurso no encontrado.

## 5. Out of scope

- Reprogramar, cancelar o eliminar rondas y heats.
- Optimización automática del cronograma.
- Calendario visual avanzado o drag and drop.
- Notificaciones a equipos y jueces.
- Zonas horarias múltiples dentro de una competencia.

## 6. Open questions

- Definir el formato HTTP definitivo para `TimeSlot` y la zona horaria asumida por la competencia.
- Definir cuánto detalle del conflicto se expone sin acoplar la API a la excepción concreta.

