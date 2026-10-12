// Contrato HTTP de la API de rondas (ver README del repositorio). No se infiere del código Java.

export interface HeatRequest {
  teamId: string;
  arenaId: string;
  /** Fecha y hora local de la competencia, ISO sin zona ni offset (p. ej. `2026-03-02T10:00:00`). */
  start: string;
  durationMinutes: number;
  judges: string[];
}

export interface ScheduleRoundRequest {
  competitionId: string;
  categoryId: string;
  challengeId: string;
  ordinal: number;
  heats: HeatRequest[];
  actor: string;
}

export interface ScheduledRound {
  roundId: string;
}

export interface Heat {
  heatId: string;
  teamId: string;
  arenaId: string;
  start: string;
  end: string;
  durationMinutes: number;
  judges: string[];
}

export interface Round {
  roundId: string;
  competitionId: string;
  categoryId: string;
  challengeId: string;
  ordinal: number;
  rulebookVersion: string;
  heats: Heat[];
}
