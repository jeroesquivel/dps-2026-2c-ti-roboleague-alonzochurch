// Contrato HTTP de `GET /runs/{runId}/score` (ver README del repositorio). No se infiere del código Java.

export type ContributionKind = 'EARNED' | 'BONUS' | 'PENALTY';

/** Una contribución al puntaje; las penalidades traen puntos negativos. */
export interface Contribution {
  ruleCode: string;
  kind: ContributionKind;
  explanation: string;
  points: number;
}

/** Bonificaciones obtenidas, tope del desafío y recorte (positivo) aplicado. */
export interface BonusCap {
  obtained: number;
  cap: number;
  trimmed: number;
  applied: number;
}

export interface SourceScore {
  source: string;
  subtotal: number;
  contributions: Contribution[];
}

/** Desglose de un desafío mixto: por fuente y ajustes que no derivan de una sola fuente. */
export interface BySource {
  sources: SourceScore[];
  acrossSources: { subtotal: number; contributions: Contribution[] };
}

/**
 * Puntaje explicado de una corrida. Si está pendiente (`completionStatus` `PENDING`) no hay total ni
 * contribuciones y `missingSources` indica qué fuentes faltan.
 */
export interface RunScore {
  runId: string;
  teamId: string;
  challengeId: string;
  rulebookVersion: string;
  completionStatus: 'PENDING' | 'COMPLETE';
  missingSources: string[];
  total: number | null;
  contributions: Contribution[] | null;
  bonusCap: BonusCap | null;
  bySource: BySource | null;
}
