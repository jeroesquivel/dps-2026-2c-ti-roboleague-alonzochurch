# Frontend

Interfaz web de RoboLeague con React, TypeScript y Vite. Consume la API REST del backend; no decide
reglas de negocio (la elegibilidad la decide siempre el backend).

## Requisitos

- Node.js 20 o superior.
- Backend en ejecución en `http://localhost:8080` (ver el README de la raíz).

## Comandos

```bash
npm install      # instalar dependencias
npm run dev      # servidor de desarrollo en http://localhost:5173
npm test         # pruebas (Vitest + Testing Library)
npm run build    # comprobación de tipos y build de producción en dist/
```

## Comunicación con el backend

En desarrollo el navegador sólo habla con el origen de Vite (`localhost:5173`). `vite.config.ts`
define un proxy que reenvía `/teams` a `http://localhost:8080`, por lo que **no hace falta
configurar CORS** en Spring. Para apuntar a otro backend:

```bash
BACKEND_URL=http://localhost:9090 npm run dev
```

Si el frontend se sirviera desde otro origen que el de la API, haría falta CORS en el backend o un
proxy equivalente. Al agregar un endpoint nuevo hay que sumar su prefijo al proxy.

## Estructura

```text
src/features/teams/
  api.ts                 cliente HTTP (POST /teams) y manejo de errores {code, message}
  types.ts               contrato de la API según el README de la raíz
  formModel.ts           estado del formulario, validación de presencia/formato y armado del request
  RegisterTeamForm.tsx   formulario: integrantes, robot y documentación
  RegistrationResult.tsx aceptación o rechazo con cada violación
```

## Flujos implementados

- **Registrar equipo** — spec [`SPEC_registrar-equipo`](../.docs/specs/SPEC_registrar-equipo.md)
  (FR-7, AC-6). El ID de la competencia y de la categoría se ingresan a mano: todavía no hay un
  endpoint para listarlos (queda para el spec de configurar evento).
