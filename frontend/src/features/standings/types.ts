// Contrato HTTP de la API de posiciones (ver README del repositorio). No se infiere del código Java.

export type PublicationStatus = 'PROVISIONAL' | 'FINAL';

export type RoundStatus = 'COUNTED' | 'DISCARDED';

export interface Tiebreak {
  code: string;
  description: string;
}

/** Resultado de una ronda dentro de un subtotal: `COUNTED` suma sus puntos y `DISCARDED` no. */
export interface RoundOutcome {
  ordinal: number;
  points: number;
  status: RoundStatus;
  reason: string;
}

export interface Subtotal {
  code: string;
  description: string;
  points: number;
  rounds: RoundOutcome[];
}

export interface StandingEntry {
  position: number;
  teamId: string;
  teamName: string;
  totalPoints: number;
  appliedTiebreaks: Tiebreak[];
  explanation: { subtotals: Subtotal[] };
}

export interface PendingRun {
  runId: string;
  teamId: string;
  teamName: string;
  roundId: string;
  attemptNumber: number;
  missingSources: string[];
}

/** Una revisión de las posiciones de una categoría. */
export interface Standings {
  competitionId: string;
  categoryId: string;
  revision: number;
  status: PublicationStatus;
  generatedAt: string;
  rulebookVersion: string;
  entries: StandingEntry[];
  pendingRuns: PendingRun[];
}

export interface Revisions {
  revisions: Standings[];
}
