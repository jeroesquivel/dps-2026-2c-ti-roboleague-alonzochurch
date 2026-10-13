# Plan: Integración REST de la funcionalidad existente

Exponer mediante HTTP los casos de uso ya implementados de RoboLeague, reorganizando la
configuración Spring y agregando adaptadores web por flujo funcional sin modificar las reglas ni
los contratos del núcleo. Este es un plan técnico de una sola ejecución: las specs existentes son
la fuente del comportamiento y de los criterios de aceptación.

## 1. Fuentes de verdad

Antes de ejecutar el plan, leer:

- [`AGENTS.md`](../../AGENTS.md), para límites arquitectónicos, comandos y cobertura;
- [`architecture.md`](../../architecture.md), para las decisiones de la aplicación web;
- [`DESIGN.md`](../../DESIGN.md), para preservar el diseño del núcleo;
- [skill `add-rest-endpoint`](../../.agents/skills/backend/add-rest-endpoint/SKILL.md), para todos
  los adaptadores HTTP de este plan;
- la spec de cada vertical antes de diseñar o implementar sus endpoints;
- los puertos de entrada involucrados y sus tests de aplicación.

No aplicar `evolve-core` ni modificar `domain` o `application` salvo que una spec revele que falta
una capacidad funcional. En ese caso, detener esa vertical: este plan no autoriza a inventar el
contrato faltante ni a cambiar reglas del core.

## 2. Estado actual

El bootstrap de la aplicación web ya dejó:

- un módulo `core` Java puro con dominio, puertos de entrada e interactors;
- un módulo `backend` Spring Boot con adaptadores en memoria;
- `RoboLeagueConfiguration`, que declara repositorios, colaboradores de dominio, dependencias
  ambientales y los 22 puertos de entrada como beans;
- una prueba de contexto que exige exactamente un bean por puerto de entrada;
- un único endpoint técnico, `GET /technical/health`.

Por lo tanto, los casos de uso ya son inyectables desde Spring. Lo pendiente es separar la
configuración por responsabilidad y construir el adaptador REST funcional: contrato HTTP, DTOs,
mappers, controllers, manejo uniforme de errores y pruebas de API.

## 3. Estado objetivo

El backend conserva una única aplicación Spring y organiza la composición y la entrada web así:

```text
backend/src/main/java/com/dps/roboleague/
|-- config/
|   |-- InfrastructureConfig.java
|   |-- DomainServicesConfig.java
|   `-- UseCasesConfig.java
`-- adapter/in/web/
    |-- error/
    |-- event/
    |-- registration/
    |-- schedule/
    |-- result/
    |-- standings/
    |-- appeal/
    `-- audit/
```

Los nombres de los paquetes funcionales pueden ajustarse al lenguaje final de la API, pero no se
crea una capa genérica de CRUD ni un controller por clase del dominio.

El recorrido obligatorio de cada operación es:

```text
HTTP request
-> request DTO
-> mapper
-> command o identificador tipado
-> puerto de entrada
-> resultado del core
-> mapper
-> response DTO
-> HTTP response
```

## 4. Alcance

Este plan incluye:

1. Dividir `RoboLeagueConfiguration` en configuraciones Spring cohesivas, preservando exactamente
   el grafo actual de objetos.
2. Mantener una única instancia compartida de cada repositorio en memoria, `AuditLog`,
   `IdGenerator` y `Clock` durante la ejecución.
3. Mantener los interactors libres de anotaciones Spring y exponerlos como beans por sus interfaces
   de puerto de entrada.
4. Definir las convenciones comunes del contrato REST necesarias para las specs existentes.
5. Agregar DTOs web y mappers propios del backend.
6. Agregar controllers que dependan exclusivamente de puertos de entrada.
7. Agregar manejo global y uniforme de errores HTTP.
8. Probar requests, responses, errores y estado compartido atravesando Spring MVC con adaptadores
   en memoria reales.
9. Actualizar `architecture.md`, `README.md` y la documentación del contrato público.
10. Ejecutar `mvn verify` y conservar la cobertura exigida del núcleo.

## 5. Fuera de alcance

- Cambiar reglas de negocio, invariantes, commands o resultados del core para simplificar JSON.
- Agregar endpoints, filtros, búsquedas, campos o puertos “por si acaso”.
- Serializar directamente agregados, entidades o value objects como contrato público.
- Inyectar repositorios o interactors concretos en controllers.
- Incorporar JPA, Spring Data, persistencia durable o transacciones de base de datos.
- Incorporar autenticación, autorización o Spring Security.
- Implementar el frontend.
- Implementar specs funcionales nuevas que todavía no pertenezcan al núcleo existente.
- Crear endpoints independientes para reglas internas como bonus cap, desafío mixto o mejores N de
  M; esas reglas se observan a través de los flujos que las consumen.

## 6. Reorganización de la configuración Spring

### 6.1 `InfrastructureConfig`

Mover desde `RoboLeagueConfiguration` los beans de adaptadores y dependencias ambientales:

- `SeasonRepository`;
- `CompetitionRepository`;
- `RulebookRepository`;
- `TeamRegistrationRepository`;
- `RoundRepository`;
- `RunResultRepository`;
- `StandingsRepository`;
- `AppealRepository`;
- `AuditLog`;
- `IdGenerator`;
- `Clock`.

Los métodos siguen devolviendo las interfaces del núcleo. Spring mantiene el scope singleton por
defecto. No agregar getters ni exponer los repositorios a los controllers.

### 6.2 `DomainServicesConfig`

Mover los colaboradores de dominio que requieren composición explícita:

- `ScheduleConflictDetector`;
- `RankingService`;
- `CategoryScoringService`.

Esta clase ensambla objetos; no contiene decisiones de negocio ni duplica cálculos del dominio.

### 6.3 `UseCasesConfig`

Mover los 22 métodos de casos de uso actuales. Cada método:

- usa como tipo de retorno el puerto de entrada;
- instancia el `*UseCase` correspondiente;
- recibe sus dependencias por parámetros;
- no consulta el `ApplicationContext`;
- no usa repositorios estáticos ni crea dependencias ambientales ocultas.

`AcceptAppealUseCase` conserva la inyección de `RecalculateStandings` por su interfaz. No reemplazar
esa relación por una llamada al contenedor.

### 6.4 Eliminación de la configuración anterior

Cuando los beans hayan sido distribuidos, eliminar `RoboLeagueConfiguration` para evitar registros
duplicados. No conservar una clase vacía o una segunda ruta de component scanning.

Actualizar `RoboLeagueApplicationTest` para comprobar:

- exactamente un bean por cada puerto de entrada;
- exactamente un bean por adaptador y colaborador compartido relevante;
- identidad compartida de los repositorios usados por distintos casos de uso;
- arranque del contexto completo.

## 7. Contrato REST transversal

Antes del primer controller funcional, cerrar y documentar estas decisiones en `architecture.md` o
en un documento de contrato de API enlazado desde allí:

1. Prefijo y estrategia de versionado de rutas.
2. Formato estable del error: código, mensaje y detalles públicos permitidos.
3. Separación uniforme entre error de formato, valor inválido, regla violada, conflicto y recurso
   inexistente, incluida la decisión `400` frente a `422`.
4. Forma de obtener el `Actor` de auditoría sin introducir autenticación en esta etapa.
5. Representación de fechas, instantes, horarios y zona horaria.
6. Convención para creación de recursos: status, cuerpo y encabezado `Location` si corresponde.
7. Representación de colecciones vacías y recursos pendientes o todavía no calculables.
8. Política de CORS sólo si existe un consumidor frontend concreto que la necesite.

Crear un `@RestControllerAdvice` compartido que traduzca la jerarquía de excepciones del dominio.
Los controllers no capturan individualmente esas excepciones salvo que una spec exija una
representación distinta.

Las validaciones web se limitan al formato y a los datos necesarios para deserializar. Las reglas
de negocio se ejecutan al construir tipos del núcleo o al invocar el puerto.

## 8. Inventario de verticales

Cada vertical se implementa y verifica completa antes de pasar a la siguiente. La ruta exacta, el
JSON y los status definitivos se cierran contra su spec; la tabla no define un CRUD automático.

| Orden | Spec | Puertos de entrada | Resultado REST esperado |
| --- | --- | --- | --- |
| 1 | `SPEC_configurar-evento.md` | `CreateSeason`, `CreateCompetition`, `PublishRulebook`, `FindCompetition` | Crear la temporada y competencia, publicar el reglamento y consultar la configuración resultante |
| 2 | `SPEC_registrar-equipo.md` | `RegisterTeam`, `FindTeamRegistration` | Registrar un equipo y consultar estado y veredicto de elegibilidad |
| 3 | `SPEC_programar-rondas.md` | `ScheduleRound`, `FindRound` | Programar una ronda y consultar heats, recursos y versión fijada |
| 4 | `SPEC_capturar-resultados.md` | `CaptureRunResult`, `RegisterAutomaticMeasurements`, `RegisterPanelEvaluations`, `FindRunResult` | Recibir las fuentes de una corrida y consultar su estado pendiente o completo |
| 5 | `SPEC_consultar-puntaje-explicable.md` | `CalculateRunScore` | Consultar total, desglose y estado pendiente sin recalcular en el adapter |
| 6 | `SPEC_gestionar-posiciones.md` | `GenerateStandings`, `GetStandings`, `PublishStandings` | Generar, consultar y publicar revisiones de posiciones |
| 7 | `SPEC_gestionar-apelaciones.md` | `SubmitAppeal`, `FindAppeal`, `AcceptAppeal`, `RejectAppeal` | Presentar, consultar y resolver apelaciones mediante operaciones separadas |
| 8 | `SPEC_consultar-auditoria.md` | `FindAuditTrail` | Consultar la traza inmutable de una operación o entidad |

`RecalculateStandings` permanece como colaboración interna de `AcceptAppeal` mientras ninguna spec
defina un actor que lo invoque directamente. Ser bean no obliga a publicar un endpoint.

Las specs `SPEC_mixed_challenge.md`, `SPEC_bonus_sum_global_limit.md` y
`SPEC-mejores-n-de-m-rondas.md` se verifican indirectamente en las respuestas de configuración,
resultados, puntaje y posiciones indicadas por las specs consumidoras. Sus declaraciones actuales
que dejan REST fuera de alcance no se convierten en endpoints nuevos.

## 9. Puertas por preguntas abiertas

Antes de implementar una vertical, revisar su sección `Open questions`. Si la respuesta cambia una
ruta, un status o una estructura JSON, documentarla en la propia spec o en el contrato HTTP y
referenciarla desde la spec.

En particular, no avanzar por suposición sobre:

- representación polimórfica de reglas de puntaje, elegibilidad, agregación y desempate al
  configurar eventos;
- formato público de `EligibilityViolation`;
- formato y zona horaria de `TimeSlot`, y detalle público de conflictos de agenda;
- visibilidad de recibos de fuentes en resultados;
- representación de puntaje pendiente;
- forma de devolver historial y última revisión de posiciones;
- detalle público de auditoría que no corresponda exponer.

Resolver una pregunta de transporte no habilita cambios funcionales en el core. Si la pregunta
descubre una capacidad ausente, separar ese trabajo en su spec y plan correspondientes.

## 10. Implementación de cada vertical

Para cada fila de la sección 8:

1. Leer completa la spec, los puertos, commands, resultados y tests de aplicación involucrados.
2. Confirmar que no quedan preguntas abiertas que cambien el contrato HTTP.
3. Diseñar rutas, métodos, status y ejemplos JSON sólo para sus operaciones requeridas.
4. Crear DTOs de request y response dentro del adaptador web.
5. Crear mappers explícitos entre DTOs y commands, identificadores o resultados tipados.
6. Crear controllers que reciban interfaces de puertos por constructor.
7. Reutilizar el manejador global para excepciones comunes.
8. Probar el recorrido feliz y todos los errores exigidos por la spec atravesando HTTP real.
9. Usar repositorios en memoria reales, reloj fijo e identificadores deterministas en los tests
   cuando el escenario dependa de tiempo o IDs.
10. Ejecutar las pruebas de la vertical y luego `mvn verify`.
11. Actualizar el contrato público y `README.md` antes de dar la vertical por terminada.

No postergar todos los tests para el final ni crear primero todos los controllers vacíos. Cada
vertical debe quedar utilizable y verificable por sí misma.

## 11. Estrategia de pruebas

### 11.1 Configuración

- El contexto inicia con las tres clases de configuración.
- Existe exactamente un bean por puerto de entrada.
- Los casos de uso comparten los mismos adaptadores singleton.
- No existe un bean duplicado proveniente de la configuración anterior.

### 11.2 Contrato HTTP compartido

- JSON o parámetros mal formados producen el error de formato acordado.
- `InvalidValueException`, `RuleViolationException`, `ConflictException` y `NotFoundException` se
  traducen uniformemente.
- Ninguna respuesta expone stack traces, nombres internos de clases o agregados serializados.

### 11.3 API por vertical

Cubrir como mínimo, cuando corresponda según la spec:

- request y response exitosos;
- consulta del estado persistido después de un comando;
- formato inválido;
- referencia inexistente;
- violación de regla;
- conflicto;
- ausencia de efectos parciales;
- orden y contenido estable de colecciones y explicaciones;
- estado pendiente frente a completo;
- auditoría generada por el caso de uso, sin escritura directa desde el controller.

No reemplazar toda la integración con mocks de puertos: debe existir cobertura que ejercite
controller, configuración real, caso de uso y adaptadores en memoria.

## 12. Secuencia de ejecución

### Paso 1 — Línea de base

1. Revisar `git status` y preservar cambios del usuario.
2. Ejecutar `mvn verify`.
3. Registrar cualquier fallo previo antes de modificar archivos.

### Paso 2 — Separar la composición

1. Crear `InfrastructureConfig`.
2. Crear `DomainServicesConfig`.
3. Crear `UseCasesConfig`.
4. Mover los beans sin cambiar constructores ni tipos públicos.
5. Eliminar `RoboLeagueConfiguration` cuando no queden beans allí.
6. Adaptar y ejecutar las pruebas de contexto.

### Paso 3 — Infraestructura web compartida

1. Cerrar las decisiones transversales de la sección 7.
2. Implementar el DTO de error y `@RestControllerAdvice`.
3. Agregar las pruebas de traducción HTTP.
4. Mantener `/technical/health` independiente del contrato funcional.

### Paso 4 — Verticales funcionales

Implementar en el orden de la sección 8. Después de cada vertical:

1. ejecutar sus pruebas;
2. ejecutar `mvn verify`;
3. actualizar el contrato público;
4. no continuar si una pregunta abierta impide definir el comportamiento observable.

### Paso 5 — Revisión arquitectónica

1. Buscar controllers que importen repositorios o clases `*UseCase` concretas.
2. Buscar anotaciones Spring, Jackson o Jakarta dentro de `core`.
3. Confirmar que ningún DTO público sea un agregado del dominio.
4. Confirmar que cada endpoint tenga un consumidor o criterio en una spec.
5. Confirmar que no se publicó un endpoint directo para `RecalculateStandings` sin requisito.

### Paso 6 — Documentación y verificación final

1. Documentar rutas, requests, responses y errores definitivos.
2. Actualizar `README.md` con ejemplos de ejecución y uso de la API.
3. Actualizar `architecture.md` con las decisiones cerradas.
4. Actualizar `DESIGN.md` sólo si cambió una decisión de diseño, no por agregar clases web.
5. Ejecutar `mvn verify` desde la raíz.
6. Iniciar el backend y recorrer por HTTP al menos un escenario completo desde configuración de
   evento hasta consulta de auditoría.

## 13. Criterios de aceptación técnicos

**AC-1 — Composición separada.**  
Given la aplicación Spring,
When inicia el contexto,
Then infraestructura, servicios de dominio y casos de uso se componen en configuraciones separadas;
and existe exactamente un bean por puerto de entrada.

**AC-2 — Core independiente.**  
Given el módulo `core`,
When se inspeccionan dependencias e imports,
Then no contiene Spring, HTTP, Jackson, Jakarta ni DTOs web.

**AC-3 — Puertos como frontera.**  
Given cualquier controller funcional,
When se inspeccionan sus dependencias,
Then depende de uno o más puertos de entrada;
and no depende de repositorios ni interactors concretos.

**AC-4 — Contrato desacoplado.**  
Given cualquier request o response funcional,
When se inspecciona el contrato JSON,
Then usa DTOs del adaptador web y no serializa agregados del dominio directamente.

**AC-5 — Errores uniformes.**  
Given errores equivalentes en verticales distintas,
When atraviesan HTTP,
Then conservan el mismo formato y criterio de status.

**AC-6 — Estado compartido.**  
Given un comando exitoso y una consulta posterior dentro de la misma aplicación,
When usan casos de uso diferentes,
Then observan el mismo estado de los adaptadores en memoria.

**AC-7 — Specs observables por HTTP.**  
Given las ocho specs funcionales de la sección 8,
When se ejecutan sus criterios de aceptación de API,
Then los recorridos exitosos y de error se verifican atravesando HTTP real.

**AC-8 — Sin publicación accidental.**  
Given los 22 puertos registrados como beans,
When se inspeccionan las rutas,
Then sólo tienen endpoint aquellos requeridos por una spec;
and `RecalculateStandings` permanece interno mientras no exista un requisito contrario.

**AC-9 — Verificación completa.**  
Given el repositorio al terminar el plan,
When se ejecuta `mvn verify`,
Then todos los módulos, tests y controles de cobertura pasan.

## 14. Riesgos y tratamiento

| Riesgo | Tratamiento |
| --- | --- |
| Duplicar beans al separar la configuración | Eliminar la clase anterior y mantener la prueba de unicidad por puerto |
| Diseñar la API como reflejo de clases Java | Derivar cada operación de la spec y del actor, no del nombre del interactor |
| Exponer el modelo interno mediante Jackson | Usar DTOs y mappers propios del adaptador |
| Resolver preguntas abiertas con supuestos | Tratar cada pregunta contractual como puerta previa de la vertical |
| Duplicar reglas en mappers o controllers | Limitar el adapter a traducción y delegar decisiones al puerto |
| Crear endpoints para todos los beans | Publicar sólo capacidades con consumidor y criterio de aceptación |
| Tests verdes con integración ficticia | Incluir pruebas HTTP con configuración y adaptadores reales |
| Estado contaminado entre pruebas | Reiniciar el contexto o proveer configuración de test determinista según el alcance |
| Plan demasiado grande para diagnosticar fallos | Terminar y verificar una vertical antes de iniciar la siguiente |

## 15. Condición de finalización

El plan está completo únicamente cuando:

- `RoboLeagueConfiguration` fue reemplazada sin duplicar ni perder beans;
- los 22 puertos siguen disponibles por sus interfaces;
- existe un contrato HTTP común documentado;
- las ocho verticales de la sección 8 están expuestas y verificadas según sus specs;
- los controllers no acceden a repositorios ni interactors concretos;
- los DTOs web no filtran el modelo interno;
- errores, tiempo, IDs y auditoría se manejan de forma uniforme y reproducible;
- `README.md` y `architecture.md` describen la API real;
- `mvn verify` pasa desde la raíz.

Después de completar este plan, los flujos nuevos pueden implementarse verticalmente reutilizando
la composición Spring, el contrato de errores y las convenciones REST ya establecidos.
