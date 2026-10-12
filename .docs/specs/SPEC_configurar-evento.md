# Spec: Configurar una temporada y su competencia

| | |
|---|---|
| **Status** | Draft |
| **Author** | |
| **Date** | 2026-10-12 |

## 1. Context

El organizador necesita preparar una edición antes de registrar equipos o programar rondas. El
flujo comprende crear una temporada, crear dentro de ella una competencia con sus categorías y
fechas, definir el reglamento inicial y publicar versiones posteriores cuando cambie la
configuración.

El núcleo ya ofrece `CreateSeason`, `CreateCompetition`, `PublishRulebook` y `FindCompetition`. Un
`RulebookDraft` reúne desafíos, requisitos de elegibilidad, agregación de intentos, desempates y
ventana de apelación. Cada `ChallengeSpec` contiene métricas, reglas de puntaje, penalizaciones,
intentos y las configuraciones F1, F2 y F3. La API y el frontend deben hacer posible este flujo sin
serializar directamente los objetos polimórficos del dominio ni incorporar reglas al adaptador web.

## 2. Functional requirements

**FR-1 — Crear temporada.** El usuario puede crear una temporada indicando nombre, año y período.
La respuesta entrega el identificador generado para continuar el flujo.

**FR-2 — Crear competencia.** El usuario puede crear una competencia dentro de una temporada
existente, indicando nombre, período y al menos una categoría con nombre, rango etario y clase de
robot.

**FR-3 — Reglamento inicial.** La creación de la competencia incluye un reglamento inicial con al
menos un desafío, requisitos de elegibilidad, política de intentos, criterios de desempate y ventana
de apelación.

**FR-4 — Configuración de desafíos.** Cada desafío permite expresar sus métricas, reglas de puntaje,
penalizaciones, bonificaciones, límite de intentos y, cuando corresponda, mejores N de M, tope global
de bonificaciones y fuentes mixtas.

**FR-5 — Resultado de creación.** La respuesta de creación entrega el identificador de la
competencia y los identificadores de todas sus categorías, conservando una correspondencia clara con
la entrada.

**FR-6 — Consultar competencia.** El usuario puede consultar nombre, período, categorías y versión
activa del reglamento de una competencia.

**FR-7 — Publicar nueva versión.** El usuario puede publicar un nuevo reglamento para una competencia
existente. La respuesta informa la versión publicada y la competencia pasa a usarla para operaciones
futuras sin modificar rondas o resultados históricos.

**FR-8 — Errores observables.** La API diferencia valores inválidos, referencias inexistentes,
violaciones de reglas y conflictos mediante el formato común de errores.

**FR-9 — Interfaz mínima.** El frontend permite ejecutar en orden la creación de temporada,
competencia y reglamento, conservar los identificadores obtenidos y consultar la competencia creada.

## 3. Constraints (non-functional)

**C-1 — Puertos existentes.** La web invoca `CreateSeason`, `CreateCompetition`, `PublishRulebook` y
`FindCompetition`; no consulta repositorios ni instancia interactors.

**C-2 — DTOs web.** Los tipos polimórficos del reglamento tienen una representación HTTP explícita.
No se exponen `RulebookDraft`, `ChallengeSpec`, `ScoringRule` ni agregados como JSON público.

**C-3 — Versionado preservado.** El adaptador no altera la semántica de versionado documentada en
`DESIGN.md`; sólo traduce la configuración recibida a tipos válidos del dominio.

**C-4 — Alcance actual.** Se usan repositorios en memoria, no hay autenticación y el actor de
auditoría puede recibirse en la petición.

**C-5 — Pruebas de API.** El recorrido feliz y los rechazos relevantes se prueban atravesando HTTP
con los adaptadores en memoria reales.

## 4. Acceptance criteria

**AC-1 — Temporada y competencia completas.**
Given datos válidos de temporada, competencia, categoría y reglamento,
When el usuario completa el flujo de configuración,
Then obtiene identificadores de temporada, competencia y categoría;
and puede consultar la competencia con su primera versión activa.

**AC-2 — Temporada inexistente.**
Given un identificador de temporada inexistente,
When se intenta crear una competencia,
Then la API responde como recurso no encontrado y no guarda la competencia.

**AC-3 — Configuración inválida.**
Given una métrica, regla, período o categoría que viola una invariante,
When se envía la configuración,
Then la API informa el error correspondiente y no crea parcialmente el evento.

**AC-4 — Nueva versión.**
Given una competencia con reglamento versión 1,
When se publica un reglamento válido nuevo,
Then la respuesta informa la versión 2;
and la consulta de competencia muestra la versión 2 como activa.

**AC-5 — Capacidades F1, F2 y F3 configurables.**
Given un reglamento que declara mejores N de M, bonus cap y un desafío mixto,
When se crea o publica,
Then la configuración es aceptada y queda disponible para los flujos que la consumen.

**AC-6 — Flujo visible.**
Given la aplicación iniciada,
When un usuario configura el evento desde el frontend,
Then puede completar el flujo sin introducir manualmente identificadores que la aplicación ya
obtuvo en pasos anteriores.

## 5. Out of scope

- Editar o eliminar temporadas, competencias, categorías o reglamentos.
- Diseñador visual genérico de fórmulas.
- Importación o exportación masiva de configuraciones.
- Persistencia durable y autenticación.
- Modificar las reglas F1, F2 o F3 ya especificadas.

## 6. Open questions

- Definir la representación HTTP concreta de cada variante de `ScoringRule`, `EligibilityRule`,
  `TiebreakRule` y `AttemptAggregation`.
- Definir si el frontend permitirá construir cualquier reglamento soportado o usará configuraciones
  predeterminadas suficientes para la demostración de esta entrega.

