import { getJson, postJson } from '../../api/http';
import type { Run } from '../appeals/types';
import type { AutomaticCaptureRequest, CompleteCaptureRequest, PanelCaptureRequest } from './types';

const RUNS = '/runs';

export function captureComplete(request: CompleteCaptureRequest): Promise<Run> {
  return postJson<Run>(RUNS, request);
}

export function captureAutomatic(request: AutomaticCaptureRequest): Promise<Run> {
  return postJson<Run>(`${RUNS}/automatic-measurements`, request);
}

export function capturePanel(request: PanelCaptureRequest): Promise<Run> {
  return postJson<Run>(`${RUNS}/panel-evaluations`, request);
}

export function findRun(runId: string): Promise<Run> {
  return getJson<Run>(`${RUNS}/${encodeURIComponent(runId)}`);
}
