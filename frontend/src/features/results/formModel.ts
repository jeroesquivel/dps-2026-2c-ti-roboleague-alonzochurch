import type {
  AutomaticCaptureRequest,
  CaptureMode,
  CompleteCaptureRequest,
  Evaluation,
  Incident,
  PanelCaptureRequest,
} from './types';

export interface CaptureFormState {
  mode: CaptureMode;
  roundId: string;
  teamId: string;
  attemptNumber: string;
  actor: string;
  measurements: string;
  evaluations: string;
  incidents: string;
}

export const emptyCaptureForm: CaptureFormState = {
  mode: 'complete',
  roundId: '',
  teamId: '',
  attemptNumber: '1',
  actor: '',
  measurements: '{\n  "TIME": 0\n}',
  evaluations: '[]',
  incidents: '[]',
};

export interface ParsedCapture {
  request: CompleteCaptureRequest | AutomaticCaptureRequest | PanelCaptureRequest | null;
  problems: string[];
}

/** Sólo valida presencia, tipos JSON y formato numérico; las reglas del desafío quedan en el backend. */
export function parseCapture(form: CaptureFormState): ParsedCapture {
  const problems: string[] = [];
  if (form.roundId.trim() === '') problems.push('El ID de la ronda es obligatorio.');
  if (form.teamId.trim() === '') problems.push('El ID del equipo es obligatorio.');
  if (form.actor.trim() === '') problems.push('El actor es obligatorio.');
  const attemptNumber = Number(form.attemptNumber);
  if (!Number.isInteger(attemptNumber) || attemptNumber < 1) {
    problems.push('El intento debe ser un entero mayor o igual a 1.');
  }

  const measurements = form.mode === 'panel' ? undefined : parseMeasurements(form.measurements, problems);
  const evaluations = form.mode === 'automatic' ? undefined : parseEvaluations(form.evaluations, problems);
  const incidents = form.mode === 'automatic' ? undefined : parseIncidents(form.incidents, problems);
  if (problems.length > 0) return { request: null, problems };

  const identity = {
    roundId: form.roundId.trim(),
    teamId: form.teamId.trim(),
    attemptNumber,
    actor: form.actor.trim(),
  };
  if (form.mode === 'automatic') {
    return { request: { ...identity, measurements: measurements! }, problems };
  }
  if (form.mode === 'panel') {
    return { request: { ...identity, evaluations: evaluations!, incidents: incidents! }, problems };
  }
  return { request: { ...identity, measurements: measurements!, evaluations: evaluations!, incidents: incidents! }, problems };
}

function parseMeasurements(text: string, problems: string[]): Record<string, number> | null {
  const value = parseJson(text, 'Las mediciones deben ser un objeto JSON válido.', problems);
  if (!isRecord(value) || Object.values(value).some((amount) => typeof amount !== 'number')) {
    problems.push('Las mediciones deben asociar cada clave con un número.');
    return null;
  }
  return value as Record<string, number>;
}

function parseEvaluations(text: string, problems: string[]): Evaluation[] | null {
  const value = parseJson(text, 'Las evaluaciones deben ser JSON válido.', problems);
  if (!Array.isArray(value) || value.some((item) => !isEvaluation(item))) {
    problems.push('Cada evaluación requiere judgeId, criterion y score numérico.');
    return null;
  }
  return value;
}

function parseIncidents(text: string, problems: string[]): Incident[] | null {
  const value = parseJson(text, 'Los incidentes deben ser JSON válido.', problems);
  if (!Array.isArray(value) || value.some((item) => !isIncident(item))) {
    problems.push('Cada incidente requiere code y occurrences numérico.');
    return null;
  }
  return value;
}

function parseJson(text: string, message: string, problems: string[]): unknown {
  try {
    return JSON.parse(text);
  } catch {
    problems.push(message);
    return null;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isEvaluation(value: unknown): value is Evaluation {
  return isRecord(value) && typeof value.judgeId === 'string' && typeof value.criterion === 'string'
    && typeof value.score === 'number';
}

function isIncident(value: unknown): value is Incident {
  return isRecord(value) && typeof value.code === 'string' && typeof value.occurrences === 'number';
}
