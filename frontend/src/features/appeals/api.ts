import { getJson, postJson } from '../../api/http';
import type {
  AcceptAppealRequest,
  Appeal,
  RejectAppealRequest,
  Run,
  RunScore,
  SubmitAppealRequest,
} from './types';

const appealPath = (appealId: string) => `/appeals/${encodeURIComponent(appealId)}`;
const runPath = (runId: string) => `/runs/${encodeURIComponent(runId)}`;

export function submitAppeal(request: SubmitAppealRequest): Promise<{ appealId: string }> {
  return postJson<{ appealId: string }>('/appeals', request);
}

export function findAppeal(appealId: string): Promise<Appeal> {
  return getJson<Appeal>(appealPath(appealId));
}

export function acceptAppeal(appealId: string, request: AcceptAppealRequest): Promise<Appeal> {
  return postJson<Appeal>(`${appealPath(appealId)}/acceptance`, request);
}

export function rejectAppeal(appealId: string, request: RejectAppealRequest): Promise<Appeal> {
  return postJson<Appeal>(`${appealPath(appealId)}/rejection`, request);
}

export function findRun(runId: string): Promise<Run> {
  return getJson<Run>(runPath(runId));
}

export function findRunScore(runId: string): Promise<RunScore> {
  return getJson<RunScore>(`${runPath(runId)}/score`);
}
