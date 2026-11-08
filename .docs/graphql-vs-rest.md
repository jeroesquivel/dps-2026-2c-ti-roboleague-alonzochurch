# GraphQL frente a REST en RoboLeague

La API GraphQL no reemplaza a REST. GraphQL concentra las consultas compuestas de solo lectura en
`POST /graphql`; REST conserva las escrituras y sigue siendo conveniente cuando su representación
coincide con lo que necesita el cliente. La comparación usa el contrato vigente y cuenta peticiones
HTTP, no llamadas internas del backend.

## Caso 1: equipo, resultados por ronda y puntaje

La pantalla de detalle necesita el nombre del equipo y, para cada ronda que disputó, el ordinal, el
número de intento, el total y las contribuciones del puntaje.

### GraphQL

```graphql
query TeamResults($teamId: ID!) {
  team(id: $teamId) {
    name
    results {
      round { ordinal }
      runs {
        attemptNumber
        score { total contributions { ruleCode points } }
      }
    }
  }
}
```

- **Peticiones:** 1.
- **Datos recibidos de más:** ninguno; la respuesta tiene sólo los campos seleccionados.
- **Datos recibidos de menos:** ninguno para esta pantalla.

### REST

REST no ofrece una colección de corridas por equipo ni permite descubrir este recorrido desde un
`teamId`. Si el cliente ya conoce los identificadores de las `K` rondas y las `R` corridas, necesita:

1. `GET /teams/{teamId}`.
2. `K` veces `GET /rounds/{roundId}`.
3. `R` veces `GET /runs/{runId}`.
4. `R` veces `GET /runs/{runId}/score`.

- **Peticiones:** `1 + K + 2R`; sin los ids previos, REST no puede completar la pantalla con el
  contrato actual.
- **Datos recibidos de más:** integrantes, documentos, robot y violaciones del equipo; heats completos
  de cada ronda; mediciones, evaluaciones, incidentes, recibos e historial de correcciones de cada
  corrida; y el desglose completo del puntaje aunque la pantalla use sólo total y contribuciones.
- **Datos recibidos de menos:** ninguna respuesta vincula todas las corridas del equipo; el cliente
  debe conocer los ids y ensamblar y ordenar el resultado.

GraphQL aporta aquí composición y descubrimiento: el resolver delega en `FindTeamRuns`, `FindRound` y
`CalculateRunScore`, y el cliente recibe el grafo armado en una petición.

## Caso 2: posiciones con nombre y robot del equipo

La pantalla necesita posición, puntos, nombre del equipo y nombre de su robot.

### GraphQL

```graphql
query Standings($competitionId: ID!, $categoryId: ID!) {
  standings(competitionId: $competitionId, categoryId: $categoryId) {
    entries {
      position
      totalPoints
      team { name robot { name } }
    }
  }
}
```

- **Peticiones:** 1, independientemente de la cantidad `N` de filas.
- **Datos recibidos de más:** ninguno.
- **Datos recibidos de menos:** ninguno para esta pantalla.
- **Acceso interno:** los `N` equipos se agrupan en una invocación a `FindTeamRegistrations`; el
  `DataLoader` mantiene caché sólo durante esa petición.

### REST

1. `GET /competitions/{competitionId}/categories/{categoryId}/standings` trae posiciones y nombres.
2. `N` veces `GET /teams/{teamId}` agrega el nombre del robot.

- **Peticiones:** `1 + N`.
- **Datos recibidos de más:** la tabla incluye revisión, estado, fecha, versión, desempates,
  explicaciones y corridas pendientes; cada equipo incluye integrantes, documentación, estado y
  violaciones que la pantalla no muestra.
- **Datos recibidos de menos:** la tabla REST no incluye el robot, por eso hace falta una petición por
  fila.

Si el cliente sólo mostrara la tabla completa sin el robot, REST sería una única petición y su
representación estable resultaría suficiente. GraphQL ofrece valor cuando el cliente necesita una
selección o composición distinta, no por sustituir toda consulta REST.

## Costos y límites conocidos

- El esquema GraphQL es otro contrato público que debe evolucionar y probarse.
- `Run.score` todavía carga el reglamento por corrida y `Team.results → round` consulta cada ronda;
  son N+1 conocidos. El lote de equipos es el punto tratado y demostrado por pruebas.
- Cada resolver abre su propia transacción; una consulta anidada no garantiza una única foto
  transaccional de todos los agregados.
- No hay mutaciones, subscriptions, paginación ni cliente GraphQL en el frontend.
