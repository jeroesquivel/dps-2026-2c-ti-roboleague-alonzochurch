import type { AppealStatus, ContributionKind, RunStatus } from './types';

export const APPEAL_STATUS_LABELS: Record<AppealStatus, string> = {
  SUBMITTED: 'Pendiente',
  ACCEPTED: 'Aceptada',
  REJECTED: 'Rechazada',
};

export const RUN_STATUS_LABELS: Record<RunStatus, string> = {
  CAPTURED: 'Capturada',
  CORRECTED: 'Corregida',
};

export const CONTRIBUTION_LABELS: Record<ContributionKind, string> = {
  EARNED: 'Puntos',
  BONUS: 'Bonus',
  PENALTY: 'Penalidad',
};

export { formatInstant, formatPoints } from '../standings/labels';

/** Texto de una medición tal como llega de la API (sin ceros de relleno). */
export function formatAmount(amount: number): string {
  return String(amount);
}
