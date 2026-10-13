import { getJson, postJson } from '../../api/http';
import type { Revisions, Standings } from './types';

function standingsPath(competitionId: string, categoryId: string): string {
  return `/competitions/${encodeURIComponent(competitionId)}/categories/${encodeURIComponent(categoryId)}/standings`;
}

export function generateStandings(competitionId: string, categoryId: string, actor: string): Promise<Standings> {
  return postJson<Standings>(standingsPath(competitionId, categoryId), { actor });
}

export function findLatestStandings(competitionId: string, categoryId: string): Promise<Standings> {
  return getJson<Standings>(standingsPath(competitionId, categoryId));
}

export function findRevisions(competitionId: string, categoryId: string): Promise<Revisions> {
  return getJson<Revisions>(`${standingsPath(competitionId, categoryId)}/revisions`);
}

export function publishStandings(competitionId: string, categoryId: string, actor: string): Promise<Standings> {
  return postJson<Standings>(`${standingsPath(competitionId, categoryId)}/publication`, { actor });
}

export function recalculateStandings(
  competitionId: string,
  categoryId: string,
  reason: string,
  actor: string,
): Promise<Standings> {
  return postJson<Standings>(`${standingsPath(competitionId, categoryId)}/recalculation`, { reason, actor });
}
