import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Standings } from '../standings/types';
import AppealsPage from './AppealsPage';
import type { RunScore } from '../scores/types';
import type { Appeal, Run } from './types';

type UserSession = ReturnType<typeof userEvent.setup>;

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

/** Responde en orden a cada llamada a `fetch`; un `Error` simula un fallo de red. */
function mockFetch(...responses: (Response | Error)[]) {
  const queue = [...responses];
  const fetchMock = vi.fn(() => {
    const next = queue.shift();
    if (next === undefined) {
      return Promise.reject(new Error('unexpected fetch'));
    }
    return next instanceof Error ? Promise.reject(next) : Promise.resolve(next);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

type Call = [string, RequestInit | undefined];

function callOf(fetchMock: ReturnType<typeof mockFetch>, index: number): Call {
  return fetchMock.mock.calls[index] as unknown as Call;
}

function bodyOf(fetchMock: ReturnType<typeof mockFetch>, index: number): unknown {
  return JSON.parse(callOf(fetchMock, index)[1]?.body as string);
}

const NOT_FOUND = jsonResponse({ code: 'NOT_FOUND', message: 'not found' }, 404);

function run(overrides: Partial<Run> = {}): Run {
  return {
    runId: 'RUN-1',
    roundId: 'ROUND-1',
    heatId: 'HEAT-1',
    teamId: 'TEAM-1',
    challengeId: 'RESCUE',
    rulebookVersion: 'v1',
    attemptNumber: 1,
    capturedAt: '2026-03-02T10:00:00.123Z',
    status: 'CAPTURED',
    currentMeasurements: { ENERGY: 42, OBJECTIVES: 4, TIME: 95.5 },
    currentIncidents: [],
    originalMeasurements: { ENERGY: 42, OBJECTIVES: 4, TIME: 95.5 },
    originalIncidents: [],
    corrections: [],
    completion: { status: 'COMPLETE', expectedSources: [], missingSources: [], receipts: [] },
    ...overrides,
  };
}

function score(overrides: Partial<RunScore> = {}): RunScore {
  return {
    runId: 'RUN-1',
    teamId: 'TEAM-1',
    challengeId: 'RESCUE',
    rulebookVersion: 'v1',
    total: 60.75,
    contributions: [
      { ruleCode: 'TIME', kind: 'EARNED', explanation: '95.500 s against a reference of 120.000 s', points: 12.25 },
      { ruleCode: 'RESTART', kind: 'PENALTY', explanation: '1 restart at 5 points', points: -5 },
    ],
    completionStatus: 'COMPLETE',
    missingSources: [],
    bonusCap: null,
    bySource: null,
    ...overrides,
  };
}

function appeal(overrides: Partial<Appeal> = {}): Appeal {
  return {
    appealId: 'APPEAL-1',
    runId: 'RUN-1',
    teamId: 'TEAM-1',
    claim: 'the timer started late',
    submittedAt: '2026-03-02T10:30:00.000Z',
    status: 'SUBMITTED',
    decision: null,
    ...overrides,
  };
}

const ACCEPTED = appeal({
  status: 'ACCEPTED',
  decision: { reviewer: 'reviewer', rationale: 'video review', decidedAt: '2026-03-02T11:00:00.000Z' },
});

const CORRECTED_RUN = run({
  status: 'CORRECTED',
  currentMeasurements: { ENERGY: 42, OBJECTIVES: 4, TIME: 80 },
  corrections: [
    {
      appliedAt: '2026-03-02T11:00:00.000Z',
      actor: 'reviewer',
      reason: 'appeal APPEAL-1 accepted',
      measurements: { ENERGY: 42, OBJECTIVES: 4, TIME: 80 },
      incidents: [],
      sourceAppeal: 'APPEAL-1',
    },
  ],
});

const ROUND = {
  roundId: 'ROUND-1',
  competitionId: 'COMPETITION-1',
  categoryId: 'CATEGORY-1',
  challengeId: 'RESCUE',
  ordinal: 1,
  rulebookVersion: 'v1',
  heats: [],
};

function standings(revision: number, totalPoints: number): Standings {
  return {
    competitionId: 'COMPETITION-1',
    categoryId: 'CATEGORY-1',
    revision,
    status: 'PROVISIONAL',
    generatedAt: '2026-03-02T10:15:30.123Z',
    rulebookVersion: 'v1',
    entries: [
      {
        position: 1,
        teamId: 'TEAM-1',
        teamName: 'Delta Bots',
        totalPoints,
        appliedTiebreaks: [],
        explanation: { subtotals: [{ code: 'TOTAL', description: 'total', points: totalPoints, rounds: [] }] },
      },
    ],
    pendingRuns: [],
  };
}

/** Respuestas de "consultar apelación": la apelación, su corrida y el puntaje de la corrida. */
function lookup(found: Appeal, currentRun: Run = run()): Response[] {
  return [jsonResponse(found, 200), jsonResponse(currentRun, 200), jsonResponse(score(), 200)];
}

async function consultAppeal(user: UserSession, appealId = 'APPEAL-1') {
  await user.type(screen.getByLabelText('ID de la apelación'), appealId);
  await user.click(screen.getByRole('button', { name: 'Consultar apelación' }));
}

async function decide(user: UserSession, rationale = 'video review') {
  await user.type(screen.getByLabelText('Revisor'), 'reviewer');
  await user.type(screen.getByLabelText('Fundamento'), rationale);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('AppealsPage', () => {
  it('shows a run with its measurements and its explained score', async () => {
    const fetchMock = mockFetch(jsonResponse(run(), 200), jsonResponse(score(), 200));
    const user = userEvent.setup();
    render(<AppealsPage />);

    await user.type(screen.getByLabelText('Corrida a consultar'), 'RUN-1');
    await user.click(screen.getByRole('button', { name: 'Consultar corrida' }));

    expect(await screen.findByRole('heading', { name: /Corrida RUN-1/ })).toHaveTextContent('Capturada');
    const measurements = screen.getByRole('table', { name: 'Mediciones vigentes' });
    expect(within(measurements).getByText('TIME').closest('tr')).toHaveTextContent('95.5');
    expect(screen.getByRole('heading', { name: /Puntaje/ })).toHaveTextContent('60.75');
    expect(screen.getByText(/Puntos TIME: 12.25/)).toHaveTextContent('95.500 s against a reference of 120.000 s');
    expect(screen.getByText(/Penalidad RESTART: -5.00/)).toBeInTheDocument();
    expect(callOf(fetchMock, 0)[0]).toBe('/runs/RUN-1');
    expect(callOf(fetchMock, 1)[0]).toBe('/runs/RUN-1/score');
    expect(screen.getByLabelText('ID de la corrida')).toHaveValue('RUN-1');
  });

  it('shows the error of the API when the run does not exist', async () => {
    mockFetch(NOT_FOUND.clone(), NOT_FOUND.clone());
    const user = userEvent.setup();
    render(<AppealsPage />);

    await user.type(screen.getByLabelText('Corrida a consultar'), 'RUN-9');
    await user.click(screen.getByRole('button', { name: 'Consultar corrida' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('not found');
    expect(screen.queryByRole('heading', { name: /Corrida/ })).not.toBeInTheDocument();
  });

  it('submits an appeal and shows it as pending next to the run it refers to', async () => {
    const fetchMock = mockFetch(jsonResponse({ appealId: 'APPEAL-1' }, 201), ...lookup(appeal()));
    const user = userEvent.setup();
    render(<AppealsPage />);

    await user.type(screen.getByLabelText('ID de la corrida'), ' RUN-1 ');
    await user.type(screen.getByLabelText('ID del equipo'), 'TEAM-1');
    await user.type(screen.getByLabelText('Reclamo'), 'the timer started late');
    await user.type(screen.getByLabelText('Presentada por'), 'team captain');
    await user.click(screen.getByRole('button', { name: 'Presentar apelación' }));

    expect(await screen.findByRole('status')).toHaveTextContent('La apelación APPEAL-1 quedó presentada');
    const [url, init] = callOf(fetchMock, 0);
    expect(url).toBe('/appeals');
    expect(init?.method).toBe('POST');
    expect(bodyOf(fetchMock, 0)).toEqual({
      runId: 'RUN-1',
      teamId: 'TEAM-1',
      claim: 'the timer started late',
      actor: 'team captain',
    });
    expect(await screen.findByRole('heading', { name: /Apelación APPEAL-1/ })).toHaveTextContent('Pendiente');
    expect(screen.getByText('the timer started late')).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Resolver apelación' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Corrida RUN-1/ })).toBeInTheDocument();
  });

  it('does not call the API when the submission is incomplete', async () => {
    const fetchMock = mockFetch();
    const user = userEvent.setup();
    render(<AppealsPage />);

    await user.click(screen.getByRole('button', { name: 'Presentar apelación' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('El ID de la corrida es obligatorio.');
    expect(alert).toHaveTextContent('El ID del equipo es obligatorio.');
    expect(alert).toHaveTextContent('El reclamo es obligatorio.');
    expect(alert).toHaveTextContent('Indicá quién presenta la apelación.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    [422, 'RULE_VIOLATION', 'team TEAM-2 cannot appeal a run of another team'],
    [422, 'RULE_VIOLATION', 'the appeal window of PT1H closed at 2026-03-02T11:00:00Z'],
    [409, 'CONFLICT', 'run RUN-1 was already appealed in appeal APPEAL-1'],
  ])('shows the refusal %i %s of an appeal without presenting it', async (status, code, message) => {
    mockFetch(jsonResponse({ code, message }, status));
    const user = userEvent.setup();
    render(<AppealsPage />);
    await user.type(screen.getByLabelText('ID de la corrida'), 'RUN-1');
    await user.type(screen.getByLabelText('ID del equipo'), 'TEAM-2');
    await user.type(screen.getByLabelText('Reclamo'), 'claim');
    await user.type(screen.getByLabelText('Presentada por'), 'someone');

    await user.click(screen.getByRole('button', { name: 'Presentar apelación' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(message);
    expect(alert).toHaveTextContent(`Código: ${code}`);
    expect(screen.queryByRole('heading', { name: /Apelación APPEAL/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('shows a pending appeal with a correction form prepared from the current result', async () => {
    const fetchMock = mockFetch(
      ...lookup(appeal(), run({ currentIncidents: [{ code: 'RESTART', occurrences: 2 }] })),
    );
    const user = userEvent.setup();
    render(<AppealsPage />);

    await consultAppeal(user);

    expect(await screen.findByRole('heading', { name: /Apelación APPEAL-1/ })).toHaveTextContent('Pendiente');
    expect(callOf(fetchMock, 0)[0]).toBe('/appeals/APPEAL-1');
    expect(screen.getByLabelText('Medición TIME')).toHaveValue('95.5');
    expect(screen.getByLabelText('Medición OBJECTIVES')).toHaveValue('4');
    expect(screen.getByLabelText('Medición ENERGY')).toHaveValue('42');
    expect(screen.getByLabelText('Código del incidente 1')).toHaveValue('RESTART');
    expect(screen.getByLabelText('Ocurrencias del incidente 1')).toHaveValue('2');
  });

  it('shows the decision of a resolved appeal and offers nothing more to resolve', async () => {
    mockFetch(...lookup(ACCEPTED, CORRECTED_RUN));
    const user = userEvent.setup();
    render(<AppealsPage />);

    await consultAppeal(user);

    expect(await screen.findByRole('heading', { name: /Apelación APPEAL-1/ })).toHaveTextContent('Aceptada');
    expect(screen.getByText('reviewer')).toBeInTheDocument();
    expect(screen.getByText('video review')).toBeInTheDocument();
    expect(screen.getByText(/2026-03-02 11:00 UTC/, { selector: 'dd' })).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Resolver apelación' })).not.toBeInTheDocument();
  });

  it('shows the error of the API when the appeal does not exist', async () => {
    mockFetch(NOT_FOUND.clone());
    const user = userEvent.setup();
    render(<AppealsPage />);

    await consultAppeal(user, 'APPEAL-9');

    expect(await screen.findByRole('alert')).toHaveTextContent('not found');
    expect(screen.queryByRole('heading', { name: /Apelación APPEAL/ })).not.toBeInTheDocument();
  });

  it('shows the appeal but cannot prepare a correction when its run cannot be loaded', async () => {
    mockFetch(jsonResponse(appeal(), 200), NOT_FOUND.clone(), NOT_FOUND.clone());
    const user = userEvent.setup();
    render(<AppealsPage />);

    await consultAppeal(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('not found');
    expect(screen.getByRole('heading', { name: /Apelación APPEAL-1/ })).toBeInTheDocument();
    expect(screen.getByText(/no se puede preparar una corrección/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Aceptar apelación' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Rechazar apelación' })).toBeEnabled();
  });

  it('accepts an appeal with the complete corrected set and shows the correction and the new revision', async () => {
    const fetchMock = mockFetch(
      ...lookup(appeal()),
      jsonResponse(ROUND, 200),
      jsonResponse({ revisions: [standings(1, 60.75)] }, 200),
      jsonResponse(ACCEPTED, 200),
      jsonResponse(CORRECTED_RUN, 200),
      jsonResponse(score({ total: 72.5 }), 200),
      jsonResponse({ revisions: [standings(1, 60.75), standings(2, 72.5)] }, 200),
    );
    const user = userEvent.setup();
    render(<AppealsPage />);
    await consultAppeal(user);
    await screen.findByRole('form', { name: 'Resolver apelación' });

    await decide(user);
    const time = screen.getByLabelText('Medición TIME');
    await user.clear(time);
    await user.type(time, '80');
    await user.click(screen.getByRole('button', { name: 'Aceptar apelación' }));

    expect(await screen.findByText(/se creó la revisión 2/)).toBeInTheDocument();
    const [url, init] = callOf(fetchMock, 5);
    expect(url).toBe('/appeals/APPEAL-1/acceptance');
    expect(init?.method).toBe('POST');
    expect(bodyOf(fetchMock, 5)).toEqual({
      rationale: 'video review',
      correctedMeasurements: { ENERGY: 42, OBJECTIVES: 4, TIME: 80 },
      correctedIncidents: [],
      reviewer: 'reviewer',
    });
    expect(callOf(fetchMock, 3)[0]).toBe('/rounds/ROUND-1');
    expect(callOf(fetchMock, 4)[0]).toBe('/competitions/COMPETITION-1/categories/CATEGORY-1/standings/revisions');
    expect(screen.getByRole('heading', { name: /Apelación APPEAL-1/ })).toHaveTextContent('Aceptada');
    expect(screen.getByText(/La apelación APPEAL-1 fue aceptada/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Corrida RUN-1/ })).toHaveTextContent('Corregida');
    expect(screen.getByRole('heading', { name: /Puntaje/ })).toHaveTextContent('72.50');
    expect(screen.getByText('Historial de correcciones (1)')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Posiciones, revisión 2/ })).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Resolver apelación' })).not.toBeInTheDocument();
  });

  it('reports that no standings were created when the category has none', async () => {
    mockFetch(
      ...lookup(appeal()),
      jsonResponse(ROUND, 200),
      NOT_FOUND.clone(),
      jsonResponse(ACCEPTED, 200),
      jsonResponse(CORRECTED_RUN, 200),
      jsonResponse(score({ total: 72.5 }), 200),
      NOT_FOUND.clone(),
    );
    const user = userEvent.setup();
    render(<AppealsPage />);
    await consultAppeal(user);
    await screen.findByRole('form', { name: 'Resolver apelación' });

    await decide(user);
    await user.click(screen.getByRole('button', { name: 'Aceptar apelación' }));

    expect(await screen.findByText(/todavía no tiene posiciones/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Corrida RUN-1/ })).toHaveTextContent('Corregida');
    expect(screen.queryByRole('heading', { name: /Posiciones, revisión/ })).not.toBeInTheDocument();
  });

  it('sends the incidents as edited and tolerates not knowing the standings', async () => {
    const fetchMock = mockFetch(
      ...lookup(appeal(), run({ currentIncidents: [{ code: 'RESTART', occurrences: 1 }] })),
      NOT_FOUND.clone(),
      jsonResponse(ACCEPTED, 200),
      jsonResponse(CORRECTED_RUN, 200),
      jsonResponse(score(), 200),
    );
    const user = userEvent.setup();
    render(<AppealsPage />);
    await consultAppeal(user);
    await screen.findByRole('form', { name: 'Resolver apelación' });

    await decide(user);
    await user.click(screen.getByRole('button', { name: 'Quitar incidente 1' }));
    await user.click(screen.getByRole('button', { name: 'Agregar incidente' }));
    await user.type(screen.getByLabelText('Código del incidente 1'), 'OUT_OF_BOUNDS');
    const occurrences = screen.getByLabelText('Ocurrencias del incidente 1');
    await user.clear(occurrences);
    await user.type(occurrences, '2');
    await user.click(screen.getByRole('button', { name: 'Aceptar apelación' }));

    expect(await screen.findByText(/No se pudo consultar si las posiciones se recalcularon/)).toBeInTheDocument();
    expect(bodyOf(fetchMock, 4)).toEqual({
      rationale: 'video review',
      correctedMeasurements: { ENERGY: 42, OBJECTIVES: 4, TIME: 95.5 },
      correctedIncidents: [{ code: 'OUT_OF_BOUNDS', occurrences: 2 }],
      reviewer: 'reviewer',
    });
  });

  it('keeps the appeal pending and shows the violation when the correction is refused', async () => {
    mockFetch(
      ...lookup(appeal()),
      jsonResponse(ROUND, 200),
      jsonResponse({ revisions: [] }, 200),
      jsonResponse({ code: 'RULE_VIOLATION', message: 'metric BOGUS is not a measurement of challenge Rescue' }, 422),
    );
    const user = userEvent.setup();
    render(<AppealsPage />);
    await consultAppeal(user);
    await screen.findByRole('form', { name: 'Resolver apelación' });

    await decide(user);
    await user.click(screen.getByRole('button', { name: 'Aceptar apelación' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('metric BOGUS is not a measurement of challenge Rescue');
    expect(alert).toHaveTextContent('Código: RULE_VIOLATION');
    expect(screen.getByRole('heading', { name: /Apelación APPEAL-1/ })).toHaveTextContent('Pendiente');
    expect(screen.getByRole('form', { name: 'Resolver apelación' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Corrida RUN-1/ })).toHaveTextContent('Capturada');
  });

  it('shows the conflict when the appeal was already resolved by someone else', async () => {
    mockFetch(
      ...lookup(appeal()),
      jsonResponse(ROUND, 200),
      NOT_FOUND.clone(),
      jsonResponse({ code: 'CONFLICT', message: 'appeal APPEAL-1 was already resolved as REJECTED' }, 409),
    );
    const user = userEvent.setup();
    render(<AppealsPage />);
    await consultAppeal(user);
    await screen.findByRole('form', { name: 'Resolver apelación' });

    await decide(user);
    await user.click(screen.getByRole('button', { name: 'Aceptar apelación' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('already resolved as REJECTED');
  });

  it('rejects an appeal with a rationale and leaves the result untouched', async () => {
    const rejected = appeal({
      status: 'REJECTED',
      decision: { reviewer: 'reviewer', rationale: 'no evidence', decidedAt: '2026-03-02T11:00:00.000Z' },
    });
    const fetchMock = mockFetch(...lookup(appeal()), jsonResponse(rejected, 200));
    const user = userEvent.setup();
    render(<AppealsPage />);
    await consultAppeal(user);
    await screen.findByRole('form', { name: 'Resolver apelación' });

    await decide(user, 'no evidence');
    await user.click(screen.getByRole('button', { name: 'Rechazar apelación' }));

    expect(await screen.findByRole('status')).toHaveTextContent('fue rechazada; el resultado y las posiciones no cambiaron');
    const [url, init] = callOf(fetchMock, 3);
    expect(url).toBe('/appeals/APPEAL-1/rejection');
    expect(init?.method).toBe('POST');
    expect(bodyOf(fetchMock, 3)).toEqual({ rationale: 'no evidence', reviewer: 'reviewer' });
    expect(screen.getByRole('heading', { name: /Apelación APPEAL-1/ })).toHaveTextContent('Rechazada');
    expect(screen.getByRole('heading', { name: /Corrida RUN-1/ })).toHaveTextContent('Capturada');
    expect(screen.queryByRole('form', { name: 'Resolver apelación' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('does not call the API when the decision is incomplete or a measurement is malformed', async () => {
    const fetchMock = mockFetch(...lookup(appeal()));
    const user = userEvent.setup();
    render(<AppealsPage />);
    await consultAppeal(user);
    await screen.findByRole('form', { name: 'Resolver apelación' });

    await user.click(screen.getByRole('button', { name: 'Rechazar apelación' }));
    const rejection = await screen.findByRole('alert');
    expect(rejection).toHaveTextContent('Indicá quién revisa la apelación.');
    expect(rejection).toHaveTextContent('El fundamento es obligatorio.');

    await decide(user);
    const time = screen.getByLabelText('Medición TIME');
    await user.clear(time);
    await user.type(time, '-3');
    await user.click(screen.getByRole('button', { name: 'Aceptar apelación' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('La medición TIME debe ser un número no negativo.');

    await user.click(screen.getByRole('button', { name: 'Agregar incidente' }));
    await user.clear(screen.getByLabelText('Ocurrencias del incidente 1'));
    await user.click(screen.getByRole('button', { name: 'Aceptar apelación' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('El incidente 1 necesita un código.');
    expect(alert).toHaveTextContent('El incidente 1 debe tener al menos una ocurrencia.');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
