import { postJson } from '../../api/http';
import type { RegisterTeamRequest, RegistrationOutcome } from './types';

export { ApiRequestError } from '../../api/http';

export function registerTeam(request: RegisterTeamRequest): Promise<RegistrationOutcome> {
  return postJson<RegistrationOutcome>('/teams', request);
}
