# Spec: Registrar y consultar un equipo

| | |
|---|---|
| **Status** | Draft |
| **Author** | |
| **Date** | 2026-10-12 |

## 1. Context

Los equipos deben inscribirse en una categoría antes de participar. La inscripción incluye nombre,
integrantes, robot y documentación. El reglamento activo determina la elegibilidad y puede rechazar
la inscripción acumulando motivos relacionados con edad, composición, especificación del robot o
documentación.

El núcleo expone `RegisterTeam` y `FindTeamRegistration`. `RegisterTeam.Outcome` entrega el
identificador, estado y `EligibilityVerdict`. La aplicación web debe mostrar ese resultado sin
reimplementar la elegibilidad en el controller o el frontend.

## 2. Functional requirements

**FR-1 — Datos de inscripción.** El usuario puede registrar un equipo indicando competencia,
categoría, nombre, integrantes con sus roles y fechas de nacimiento, robot y documentos.

**FR-2 — Evaluación de elegibilidad.** La inscripción se evalúa con los requisitos del reglamento
activo de la competencia.

**FR-3 — Resultado explicable.** La respuesta informa el identificador del equipo, estado de la
inscripción y todas las violaciones de elegibilidad detectadas, no sólo la primera.

**FR-4 — Inscripción aceptada.** Un equipo que cumple todos los requisitos queda aceptado y puede
usarse posteriormente al programar una ronda.

**FR-5 — Inscripción rechazada.** Un equipo con datos bien formados pero inelegible queda registrado
con estado rechazado y conserva los motivos para su consulta.

**FR-6 — Consultar inscripción.** El usuario puede consultar una inscripción por `TeamId` y ver sus
datos, estado y veredicto.

**FR-7 — Interfaz mínima.** El frontend permite cargar integrantes, robot y documentación, enviar la
inscripción y presentar de forma diferenciada la aceptación o los motivos del rechazo.

## 3. Constraints (non-functional)

**C-1 — Autoridad del dominio.** Sólo `RegisterTeam` decide elegibilidad. La validación del frontend
se limita a presencia y formato para mejorar la experiencia.

**C-2 — Puerto de consulta.** La consulta usa `FindTeamRegistration`; el controller no accede a
`TeamRegistrationRepository`.

**C-3 — DTOs propios.** El contrato web no serializa directamente `TeamRegistration`, `Robot`,
`TeamMembers` o `EligibilityVerdict`.

**C-4 — Reglamento activo.** El adaptador no permite elegir una versión arbitraria del reglamento
para evaluar la inscripción.

**C-5 — Pruebas.** Las pruebas de API cubren aceptación, rechazo con múltiples motivos, referencias
inexistentes y entradas mal formadas.

## 4. Acceptance criteria

**AC-1 — Equipo elegible.**
Given una competencia y categoría existentes con un reglamento publicado,
When se registra un equipo que cumple todos los requisitos,
Then la API devuelve su identificador y estado aceptado;
and la consulta posterior conserva ese resultado.

**AC-2 — Violaciones acumuladas.**
Given un equipo que viola más de un requisito de elegibilidad,
When se registra,
Then la respuesta queda rechazada y enumera todas las violaciones esperadas.

**AC-3 — Categoría inexistente o ajena.**
Given una categoría inexistente o que no pertenece a la competencia indicada,
When se intenta registrar el equipo,
Then la API informa el error y no crea una inscripción válida.

**AC-4 — Datos inválidos.**
Given un integrante, robot, documento o nombre que viola una invariante,
When se envía la inscripción,
Then la API devuelve el error de entrada correspondiente.

**AC-5 — Consulta inexistente.**
Given un `TeamId` desconocido,
When se consulta la inscripción,
Then la API responde como recurso no encontrado.

**AC-6 — Presentación del veredicto.**
Given una inscripción evaluada,
When el frontend muestra el resultado,
Then distingue aceptación de rechazo y presenta cada violación con información comprensible.

## 5. Out of scope

- Editar o eliminar una inscripción.
- Reconsiderar automáticamente una inscripción al publicar otro reglamento.
- Adjuntar archivos binarios; los documentos conservan la representación soportada por el dominio.
- Autenticación del equipo o del organizador.
- Duplicar las reglas de elegibilidad en la interfaz.

## 6. Open questions

Resuelta: cada `EligibilityViolation` se expone como `{"code", "reason"}`. El `code` es el
`EligibilityRuleCode` estable de la regla y `reason` es una descripción que no debe interpretarse
(contrato en el `README.md`).
