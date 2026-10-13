import { getJson, postJson } from '../../api/http';
import type {
  AutomaticCaptureRequest,
  CompleteCaptureRequest,
  PanelCaptureRequest,
  RunResult,
} from './types';

const RUNS = '/api/runs';

export function captureComplete(request: CompleteCaptureRequest): Promise<RunResult> {
  return postJson<RunResult>(RUNS, request);
}

export function captureAutomatic(request: AutomaticCaptureRequest): Promise<RunResult> {
  return postJson<RunResult>(`${RUNS}/automatic-measurements`, request);
}

export function capturePanel(request: PanelCaptureRequest): Promise<RunResult> {
  return postJson<RunResult>(`${RUNS}/panel-evaluations`, request);
}

export function findRunResult(runId: string): Promise<RunResult> {
  return getJson<RunResult>(`${RUNS}/${encodeURIComponent(runId)}`);
}
