import type { Evaluation, Incident } from '../appeals/types';

export type { Evaluation, Incident };
export type CaptureMode = 'complete' | 'automatic' | 'panel';

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
