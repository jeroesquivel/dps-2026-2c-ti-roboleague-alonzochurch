import type { ContributionKind } from './types';

export const CONTRIBUTION_LABELS: Record<ContributionKind, string> = {
  EARNED: 'Puntos',
  BONUS: 'Bonus',
  PENALTY: 'Penalidad',
};

/** Nombre de cada fuente de un desafío mixto, como título de su grupo. */
export const SOURCE_TITLES: Record<string, string> = {
  AUTOMATIC: 'Mediciones automáticas',
  JUDGES: 'Panel de jueces',
};

/** Fuente que falta, dicha dentro de una frase («faltan …»). */
export const MISSING_SOURCE_LABELS: Record<string, string> = {
  AUTOMATIC: 'las mediciones automáticas',
  JUDGES: 'la evaluación del jurado',
};

export { formatPoints } from '../standings/labels';
