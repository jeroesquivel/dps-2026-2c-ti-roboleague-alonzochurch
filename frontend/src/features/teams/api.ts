import { getJson, postJson } from '../../api/http';
import type { RegisterTeamRequest, RegistrationOutcome, TeamRegistration } from './types';

export { ApiRequestError } from '../../api/http';

export function registerTeam(request: RegisterTeamRequest): Promise<RegistrationOutcome> {
  return postJson<RegistrationOutcome>('/teams', request);
}

export function findTeam(teamId: string): Promise<TeamRegistration> {
  return getJson<TeamRegistration>(`/teams/${encodeURIComponent(teamId)}`);
}
