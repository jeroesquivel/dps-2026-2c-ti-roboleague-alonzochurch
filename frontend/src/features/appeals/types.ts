// Contrato HTTP de la API de apelaciones y corridas (ver README del repositorio). No se infiere del código Java.

export type AppealStatus = 'SUBMITTED' | 'ACCEPTED' | 'REJECTED';

export interface Decision {
  reviewer: string;
  rationale: string;
  decidedAt: string;
}

/** Estado de una apelación; `decision` es `null` mientras está pendiente (`SUBMITTED`). */
export interface Appeal {
  appealId: string;
  runId: string;
  teamId: string;
  claim: string;
  submittedAt: string;
  status: AppealStatus;
  decision: Decision | null;
}

export interface SubmitAppealRequest {
  runId: string;
  teamId: string;
  claim: string;
  actor: string;
}

export interface Incident {
  code: string;
  occurrences: number;
}

/** Conjunto completo de mediciones e incidentes que reemplaza al resultado actual de la corrida. */
export interface AcceptAppealRequest {
  rationale: string;
  correctedMeasurements: Record<string, number>;
  correctedIncidents: Incident[];
  reviewer: string;
}

export interface RejectAppealRequest {
  rationale: string;
  reviewer: string;
}

export type RunStatus = 'CAPTURED' | 'CORRECTED';

export interface Correction {
  appliedAt: string;
  actor: string;
  reason: string;
  measurements: Record<string, number>;
  incidents: Incident[];
  sourceAppeal: string;
}

/** Evaluación de un juez para un criterio, tal como la recibió la corrida. */
export interface Evaluation {
  judgeId: string;
  criterion: string;
  score: number;
}

export interface Receipt {
  source: string;
  actor: string;
  receivedAt: string;
}

export interface Completion {
  status: 'PENDING' | 'COMPLETE';
  expectedSources: string[];
  missingSources: string[];
  receipts: Receipt[];
}

/** Detalle de una corrida: resultado vigente, original capturado y correcciones aplicadas. */
export interface Run {
  runId: string;
  roundId: string;
  heatId: string;
  teamId: string;
  challengeId: string;
  rulebookVersion: string;
  attemptNumber: number;
  capturedAt: string;
  status: RunStatus;
  currentMeasurements: Record<string, number>;
  currentIncidents: Incident[];
  evaluations: Evaluation[];
  originalMeasurements: Record<string, number>;
  originalIncidents: Incident[];
  corrections: Correction[];
  completion: Completion;
}
