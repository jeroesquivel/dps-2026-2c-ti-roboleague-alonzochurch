import { getJson } from '../../api/http';
import type { RunScore } from './types';

export function findRunScore(runId: string): Promise<RunScore> {
  return getJson<RunScore>(`/runs/${encodeURIComponent(runId)}/score`);
}
