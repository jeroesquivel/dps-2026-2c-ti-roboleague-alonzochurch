import { formatAmount } from './labels';
import type { AcceptAppealRequest, Incident, RejectAppealRequest, Run, SubmitAppealRequest } from './types';

export interface MeasurementRow {
  key: string;
  value: string;
}

export interface IncidentRow {
  code: string;
  occurrences: string;
}

export interface ResolutionDraft {
  reviewer: string;
  rationale: string;
  measurements: MeasurementRow[];
  incidents: IncidentRow[];
}

/** Borrador inicial de una corrección: el resultado vigente de la corrida, listo para editar. */
export function draftOf(run: Run | null): ResolutionDraft {
  return {
    reviewer: '',
    rationale: '',
    measurements: Object.entries(run?.currentMeasurements ?? {}).map(([key, value]) => ({
      key,
      value: formatAmount(value),
    })),
    incidents: (run?.currentIncidents ?? []).map((incident) => ({
      code: incident.code,
      occurrences: String(incident.occurrences),
    })),
  };
}

const AMOUNT = /^\d+(\.\d+)?$/;
const COUNT = /^\d+$/;

/** Sólo presencia y formato: las reglas del reglamento las valida el backend. */
export function rejectionProblems(draft: Pick<ResolutionDraft, 'reviewer' | 'rationale'>): string[] {
  const problems: string[] = [];
  if (draft.reviewer.trim() === '') {
    problems.push('Indicá quién revisa la apelación.');
  }
  if (draft.rationale.trim() === '') {
    problems.push('El fundamento es obligatorio.');
  }
  return problems;
}

export function acceptanceProblems(draft: ResolutionDraft): string[] {
  const problems = rejectionProblems(draft);
  for (const row of draft.measurements) {
    if (!AMOUNT.test(row.value.trim())) {
      problems.push(`La medición ${row.key} debe ser un número no negativo.`);
    }
  }
  draft.incidents.forEach((row, index) => {
    if (row.code.trim() === '') {
      problems.push(`El incidente ${index + 1} necesita un código.`);
    }
    if (!COUNT.test(row.occurrences.trim()) || Number(row.occurrences) < 1) {
      problems.push(`El incidente ${index + 1} debe tener al menos una ocurrencia.`);
    }
  });
  return problems;
}

export function toAcceptRequest(draft: ResolutionDraft): AcceptAppealRequest {
  const incidents: Incident[] = draft.incidents.map((row) => ({
    code: row.code.trim(),
    occurrences: Number(row.occurrences),
  }));
  return {
    rationale: draft.rationale.trim(),
    correctedMeasurements: Object.fromEntries(draft.measurements.map((row) => [row.key, Number(row.value.trim())])),
    correctedIncidents: incidents,
    reviewer: draft.reviewer.trim(),
  };
}

export function toRejectRequest(draft: Pick<ResolutionDraft, 'reviewer' | 'rationale'>): RejectAppealRequest {
  return { rationale: draft.rationale.trim(), reviewer: draft.reviewer.trim() };
}

export function submissionProblems(form: SubmitAppealRequest): string[] {
  const problems: string[] = [];
  if (form.runId.trim() === '') {
    problems.push('El ID de la corrida es obligatorio.');
  }
  if (form.teamId.trim() === '') {
    problems.push('El ID del equipo es obligatorio.');
  }
  if (form.claim.trim() === '') {
    problems.push('El reclamo es obligatorio.');
  }
  if (form.actor.trim() === '') {
    problems.push('Indicá quién presenta la apelación.');
  }
  return problems;
}
