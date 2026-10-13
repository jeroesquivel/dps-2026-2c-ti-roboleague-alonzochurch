export type CaptureMode = 'complete' | 'automatic' | 'panel';
export type ResultSource = 'AUTOMATIC' | 'JUDGES';

export interface Evaluation {
  judgeId: string;
  criterion: string;
  score: number;
}

export interface Incident {
  code: string;
  occurrences: number;
}

export interface SourceState {
  source: ResultSource;
  received: boolean;
  actor: string | null;
  receivedAt: string | null;
}

/** Contrato observable de la API de resultados; no representa el agregado del dominio. */
export interface RunResult {
  runId: string;
  roundId: string;
  heatId: string;
  teamId: string;
  challengeId: string;
  attemptNumber: number;
  rulebookVersion: number;
  capturedAt: string;
  completionStatus: 'PENDING' | 'COMPLETE';
  missingSources: ResultSource[];
  sources: SourceState[];
  measurements: Record<string, number>;
  evaluations: Evaluation[];
  incidents: Incident[];
  correctionStatus: 'CAPTURED' | 'CORRECTED';
  correctionCount: number;
}

interface CaptureIdentity {
  roundId: string;
  teamId: string;
  attemptNumber: number;
  actor: string;
}

export interface CompleteCaptureRequest extends CaptureIdentity {
  measurements: Record<string, number>;
  evaluations: Evaluation[];
  incidents: Incident[];
}

export interface AutomaticCaptureRequest extends CaptureIdentity {
  measurements: Record<string, number>;
}

export interface PanelCaptureRequest extends CaptureIdentity {
  evaluations: Evaluation[];
  incidents: Incident[];
}
