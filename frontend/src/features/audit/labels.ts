export const ACTION_LABELS: Record<string, string> = {
  SEASON_CREATED: 'Temporada creada',
  COMPETITION_CREATED: 'Competencia creada',
  RULEBOOK_PUBLISHED: 'Reglamento publicado',
  TEAM_REGISTERED: 'Equipo inscripto',
  TEAM_REJECTED: 'Inscripción rechazada',
  ROUND_SCHEDULED: 'Ronda programada',
  RESULT_CAPTURED: 'Resultado capturado',
  RESULT_SOURCE_RECEIVED: 'Fuente de resultado recibida',
  RESULT_CORRECTED: 'Resultado corregido',
  STANDINGS_GENERATED: 'Posiciones generadas',
  STANDINGS_PUBLISHED: 'Posiciones publicadas',
  STANDINGS_RECALCULATED: 'Posiciones recalculadas',
  APPEAL_SUBMITTED: 'Apelación presentada',
  APPEAL_RESOLVED: 'Apelación resuelta',
};

export const DETAIL_LABELS: Record<string, string> = {
  rulebook: 'Reglamento',
  version: 'Versión',
  violations: 'Incumplimientos',
  heats: 'Turnos',
  measurements: 'Mediciones',
  source: 'Fuente',
  evaluations: 'Evaluaciones',
  incidents: 'Incidentes',
  original: 'Valores originales',
  corrected: 'Valores corregidos',
  reason: 'Motivo',
  revision: 'Revisión',
  run: 'Corrida',
  status: 'Estado',
  rationale: 'Fundamento',
};

/** Etiqueta de la acción; una acción que el frontend no conoce se muestra con su código. */
export function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action;
}

/** Etiqueta del detalle; un detalle que el frontend no conoce se muestra con su clave. */
export function detailLabel(key: string): string {
  return DETAIL_LABELS[key] ?? key;
}

/** Instante ISO mostrado en UTC y con segundos, para que no dependa de la zona del navegador. */
export function formatMoment(iso: string): string {
  return `${iso.slice(0, 19).replace('T', ' ')} UTC`;
}

export const SUBJECT_EXAMPLES = 'RUN-1, APPEAL-1, ROUND-1, COMPETITION-1, CATEGORY-1, TEAM-1, SEASON-1';
