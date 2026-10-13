import { getJson, postJson } from '../../api/http';
import type {
  Competition,
  CompetitionCreated,
  CreateCompetitionRequest,
  CreateSeasonRequest,
  PublishRulebookRequest,
  RulebookPublished,
  SeasonCreated,
} from './types';

export function createSeason(request: CreateSeasonRequest): Promise<SeasonCreated> {
  return postJson<SeasonCreated>('/seasons', request);
}

export function createCompetition(seasonId: string, request: CreateCompetitionRequest): Promise<CompetitionCreated> {
  return postJson<CompetitionCreated>(`/seasons/${encodeURIComponent(seasonId)}/competitions`, request);
}

export function findCompetition(competitionId: string): Promise<Competition> {
  return getJson<Competition>(`/competitions/${encodeURIComponent(competitionId)}`);
}

export function publishRulebook(competitionId: string, request: PublishRulebookRequest): Promise<RulebookPublished> {
  return postJson<RulebookPublished>(`/competitions/${encodeURIComponent(competitionId)}/rulebooks`, request);
}
