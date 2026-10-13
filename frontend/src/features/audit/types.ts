// Contrato HTTP de `GET /audit?subjectId=...` (ver README del repositorio). No se infiere del código Java.

/** Un evento de la traza. `details` sólo trae las claves que se registraron para esa acción. */
export interface AuditEvent {
  occurredAt: string;
  action: string;
  subjectId: string;
  actor: string;
  details: Record<string, string>;
}

/** Traza de un sujeto, ya ordenada por el servidor; `events` vacío si no tiene eventos. */
export interface AuditTrail {
  subjectId: string;
  events: AuditEvent[];
}
