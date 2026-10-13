// Contrato HTTP de la configuración del evento (ver README del repositorio). No se infiere del código Java.

export interface Period {
  start: string;
  end: string;
}

export interface CreateSeasonRequest {
  name: string;
  year: number;
  period: Period;
  actor: string;
}

export interface SeasonCreated {
  seasonId: string;
}

export interface CategoryRequest {
  name: string;
  minimumAge: number;
  maximumAge: number;
  robotClass: string;
}

/** El reglamento se envía tal cual lo escribió el usuario (JSON del contrato); el servidor lo valida. */
export type RulebookJson = Record<string, unknown>;

export interface CreateCompetitionRequest {
  name: string;
  period: Period;
  categories: CategoryRequest[];
  rulebook: RulebookJson;
  actor: string;
}

export interface CompetitionCreated {
  competitionId: string;
  rulebookVersion: string;
  /** En el mismo orden que las categorías enviadas. */
  categories: { categoryId: string; name: string }[];
}

export interface Competition {
  competitionId: string;
  name: string;
  period: Period;
  rulebookVersion: string;
  categories: {
    categoryId: string;
    name: string;
    minimumAge: number;
    maximumAge: number;
    robotClass: string;
  }[];
}

export interface PublishRulebookRequest {
  rulebook: RulebookJson;
  actor: string;
}

export interface RulebookPublished {
  competitionId: string;
  rulebookVersion: string;
}
