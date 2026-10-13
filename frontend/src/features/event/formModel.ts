import { presetText } from './presets';
import type {
  CategoryRequest,
  CreateCompetitionRequest,
  CreateSeasonRequest,
  PublishRulebookRequest,
  RulebookJson,
} from './types';

export interface SeasonDraft {
  name: string;
  year: string;
  start: string;
  end: string;
}

export interface CategoryDraft {
  name: string;
  minimumAge: string;
  maximumAge: string;
  robotClass: string;
}

export interface CompetitionDraft {
  seasonId: string;
  name: string;
  start: string;
  end: string;
  categories: CategoryDraft[];
  rulebook: string;
}

export const DEFAULT_ACTOR = 'organizer';
export const DEFAULT_PRESET = 'rescue';

export function emptySeason(): SeasonDraft {
  return { name: '', year: '', start: '', end: '' };
}

export function emptyCategory(): CategoryDraft {
  return { name: '', minimumAge: '', maximumAge: '', robotClass: '' };
}

export function emptyCompetition(seasonId = ''): CompetitionDraft {
  return {
    seasonId,
    name: '',
    start: '',
    end: '',
    categories: [emptyCategory()],
    rulebook: presetText(DEFAULT_PRESET),
  };
}

function isWholeNumber(text: string): boolean {
  return /^\d+$/.test(text.trim());
}

/** Resultado de leer el JSON escrito por el usuario: el reglamento o el motivo por el que no se pudo leer. */
export function parseRulebook(text: string): { rulebook: RulebookJson } | { problem: string } {
  if (text.trim() === '') {
    return { problem: 'El reglamento es obligatorio.' };
  }
  try {
    const parsed: unknown = JSON.parse(text);
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return { problem: 'El reglamento debe ser un objeto JSON.' };
    }
    return { rulebook: parsed as RulebookJson };
  } catch {
    return { problem: 'El reglamento no es un JSON válido.' };
  }
}

function requiredText(value: string, message: string, problems: string[]) {
  if (value.trim() === '') {
    problems.push(message);
  }
}

/** Controles de presencia y formato previos al envío; las reglas de negocio las decide el servidor. */
export function seasonProblems(draft: SeasonDraft, actor: string): string[] {
  const problems: string[] = [];
  requiredText(draft.name, 'El nombre de la temporada es obligatorio.', problems);
  if (!isWholeNumber(draft.year)) {
    problems.push('El año debe ser un número entero.');
  }
  requiredText(draft.start, 'El inicio de la temporada es obligatorio.', problems);
  requiredText(draft.end, 'El fin de la temporada es obligatorio.', problems);
  requiredText(actor, 'Indicá quién configura el evento.', problems);
  return problems;
}

export function seasonRequest(draft: SeasonDraft, actor: string): CreateSeasonRequest {
  return {
    name: draft.name.trim(),
    year: Number(draft.year),
    period: { start: draft.start, end: draft.end },
    actor: actor.trim(),
  };
}

export function competitionProblems(draft: CompetitionDraft, actor: string): string[] {
  const problems: string[] = [];
  requiredText(draft.seasonId, 'El ID de la temporada es obligatorio.', problems);
  requiredText(draft.name, 'El nombre de la competencia es obligatorio.', problems);
  requiredText(draft.start, 'El inicio de la competencia es obligatorio.', problems);
  requiredText(draft.end, 'El fin de la competencia es obligatorio.', problems);
  requiredText(actor, 'Indicá quién configura el evento.', problems);
  if (draft.categories.length === 0) {
    problems.push('Se requiere al menos una categoría.');
  }
  draft.categories.forEach((category, index) => {
    const prefix = `Categoría ${index + 1}: `;
    requiredText(category.name, `${prefix}el nombre es obligatorio.`, problems);
    if (!isWholeNumber(category.minimumAge) || !isWholeNumber(category.maximumAge)) {
      problems.push(`${prefix}las edades mínima y máxima deben ser números enteros.`);
    }
    requiredText(category.robotClass, `${prefix}la clase de robot es obligatoria.`, problems);
  });
  const rulebook = parseRulebook(draft.rulebook);
  if ('problem' in rulebook) {
    problems.push(rulebook.problem);
  }
  return problems;
}

export function competitionRequest(draft: CompetitionDraft, actor: string): CreateCompetitionRequest {
  const rulebook = parseRulebook(draft.rulebook);
  if ('problem' in rulebook) {
    throw new Error(rulebook.problem);
  }
  return {
    name: draft.name.trim(),
    period: { start: draft.start, end: draft.end },
    categories: draft.categories.map(
      (category): CategoryRequest => ({
        name: category.name.trim(),
        minimumAge: Number(category.minimumAge),
        maximumAge: Number(category.maximumAge),
        robotClass: category.robotClass.trim(),
      }),
    ),
    rulebook: rulebook.rulebook,
    actor: actor.trim(),
  };
}

export function publishProblems(competitionId: string, rulebookText: string, actor: string): string[] {
  const problems: string[] = [];
  requiredText(competitionId, 'El ID de la competencia es obligatorio.', problems);
  requiredText(actor, 'Indicá quién configura el evento.', problems);
  const rulebook = parseRulebook(rulebookText);
  if ('problem' in rulebook) {
    problems.push(rulebook.problem);
  }
  return problems;
}

export function publishRequest(rulebookText: string, actor: string): PublishRulebookRequest {
  const rulebook = parseRulebook(rulebookText);
  if ('problem' in rulebook) {
    throw new Error(rulebook.problem);
  }
  return { rulebook: rulebook.rulebook, actor: actor.trim() };
}
