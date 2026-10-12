import type { ScheduleRoundRequest } from './types';

export interface HeatDraft {
  teamId: string;
  arenaId: string;
  /** Valor de un `<input type="datetime-local">` (`YYYY-MM-DDTHH:mm`). */
  start: string;
  durationMinutes: string;
  /** Identificadores de jueces separados por coma. */
  judges: string;
}

export interface FormState {
  competitionId: string;
  categoryId: string;
  challengeId: string;
  ordinal: string;
  actor: string;
  heats: HeatDraft[];
}

export const CONFLICT_LABELS: Record<string, string> = {
  TEAM_BUSY: 'Equipo ocupado',
  ARENA_BUSY: 'Pista ocupada',
  JUDGE_BUSY: 'Juez ocupado',
};

export function emptyHeat(): HeatDraft {
  return { teamId: '', arenaId: '', start: '', durationMinutes: '', judges: '' };
}

export function emptyForm(): FormState {
  return { competitionId: '', categoryId: '', challengeId: '', ordinal: '', actor: '', heats: [emptyHeat()] };
}

export function judgesOf(text: string): string[] {
  return text
    .split(',')
    .map((judge) => judge.trim())
    .filter((judge) => judge.length > 0);
}

function isPositiveInteger(text: string): boolean {
  return /^\d+$/.test(text.trim()) && Number(text) >= 1;
}

/** Controles de presencia y formato previos al envío; las reglas de negocio las decide el servidor. */
export function problemsOf(form: FormState): string[] {
  const problems: string[] = [];
  const required: [string, string][] = [
    [form.competitionId, 'El ID de la competencia es obligatorio.'],
    [form.categoryId, 'El ID de la categoría es obligatorio.'],
    [form.challengeId, 'El ID del desafío es obligatorio.'],
    [form.actor, 'Indicá quién programa la ronda.'],
  ];
  required.forEach(([value, message]) => {
    if (value.trim() === '') {
      problems.push(message);
    }
  });
  if (!isPositiveInteger(form.ordinal)) {
    problems.push('El número de ronda debe ser un entero mayor o igual a 1.');
  }
  form.heats.forEach((heat, index) => {
    const prefix = `Largada ${index + 1}: `;
    if (heat.teamId.trim() === '') {
      problems.push(`${prefix}el ID del equipo es obligatorio.`);
    }
    if (heat.arenaId.trim() === '') {
      problems.push(`${prefix}el ID de la pista es obligatorio.`);
    }
    if (heat.start.trim() === '') {
      problems.push(`${prefix}la fecha y hora de inicio es obligatoria.`);
    }
    if (!isPositiveInteger(heat.durationMinutes)) {
      problems.push(`${prefix}la duración debe ser un entero de minutos mayor o igual a 1.`);
    }
    if (judgesOf(heat.judges).length === 0) {
      problems.push(`${prefix}se requiere al menos un juez.`);
    }
  });
  return problems;
}

/** El campo `datetime-local` omite los segundos cuando valen cero; la API los espera siempre. */
function withSeconds(start: string): string {
  const trimmed = start.trim();
  return /T\d{2}:\d{2}$/.test(trimmed) ? `${trimmed}:00` : trimmed;
}

export function toRequest(form: FormState): ScheduleRoundRequest {
  return {
    competitionId: form.competitionId.trim(),
    categoryId: form.categoryId.trim(),
    challengeId: form.challengeId.trim(),
    ordinal: Number(form.ordinal),
    heats: form.heats.map((heat) => ({
      teamId: heat.teamId.trim(),
      arenaId: heat.arenaId.trim(),
      start: withSeconds(heat.start),
      durationMinutes: Number(heat.durationMinutes),
      judges: judgesOf(heat.judges),
    })),
    actor: form.actor.trim(),
  };
}
