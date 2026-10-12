import { getJson, postJson } from '../../api/http';
import type { Round, ScheduleRoundRequest, ScheduledRound } from './types';

export function scheduleRound(request: ScheduleRoundRequest): Promise<ScheduledRound> {
  return postJson<ScheduledRound>('/rounds', request);
}

export function findRound(roundId: string): Promise<Round> {
  return getJson<Round>(`/rounds/${encodeURIComponent(roundId)}`);
}
