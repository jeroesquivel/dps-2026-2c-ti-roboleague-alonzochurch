import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ResultsPage from './ResultsPage';

const COMPLETE = {
  runId: 'RUN-1', roundId: 'ROUND-1', heatId: 'HEAT-1', teamId: 'TEAM-1', challengeId: 'RESCUE',
  attemptNumber: 1, rulebookVersion: 1, capturedAt: '2026-03-02T10:00:00Z', completionStatus: 'COMPLETE',
  missingSources: [],
  sources: [
    { source: 'AUTOMATIC', received: false, actor: null, receivedAt: null },
    { source: 'JUDGES', received: false, actor: null, receivedAt: null },
  ],
  measurements: { TIME: 70 }, evaluations: [{ judgeId: 'J1', criterion: 'DESIGN', score: 8 }],
  incidents: [{ code: 'RESTART', occurrences: 1 }], correctionStatus: 'CAPTURED', correctionCount: 0,
};

const AUTOMATIC_PENDING = {
  ...COMPLETE, challengeId: 'SHOWCASE', completionStatus: 'PENDING', missingSources: ['JUDGES'],
  evaluations: [], incidents: [],
  sources: [
    { source: 'AUTOMATIC', received: true, actor: 'track-system', receivedAt: '2026-03-02T10:00:00Z' },
    { source: 'JUDGES', received: false, actor: null, receivedAt: null },
  ],
};

function response(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function mockFetch(...responses: Response[]) {
  const queue = [...responses];
  const fetchMock = vi.fn(() => Promise.resolve(queue.shift() ?? response({}, 500)));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function fillIdentity(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('ID de la ronda'), 'ROUND-1');
  await user.type(screen.getByLabelText('ID del equipo'), 'TEAM-1');
  await user.type(screen.getByLabelText('Registrado por'), 'scorekeeper');
}

afterEach(() => vi.unstubAllGlobals());

describe('ResultsPage', () => {
  it('captures a complete result and renders all observable data', async () => {
    const fetchMock = mockFetch(response(COMPLETE));
    const user = userEvent.setup();
    render(<ResultsPage />);
    await fillIdentity(user);
    fireEvent.change(screen.getByLabelText('Mediciones'), { target: { value: '{"TIME":70}' } });
    fireEvent.change(screen.getByLabelText('Evaluaciones'), {
      target: { value: '[{"judgeId":"J1","criterion":"DESIGN","score":8}]' },
    });
    fireEvent.change(screen.getByLabelText('Incidentes'), {
      target: { value: '[{"code":"RESTART","occurrences":1}]' },
    });
    await user.click(screen.getByRole('button', { name: 'Registrar resultado' }));

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('Corrida RUN-1');
    expect(status).toHaveTextContent('Completa');
    expect(status).toHaveTextContent('Reglamentov1');
    expect(status).toHaveTextContent('J1 · DESIGN: 8');
    expect(status).toHaveTextContent('RESTART × 1');
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/runs');
    expect(JSON.parse(init.body as string)).toMatchObject({
      roundId: 'ROUND-1', teamId: 'TEAM-1', attemptNumber: 1, actor: 'scorekeeper',
      measurements: { TIME: 70 },
    });
  });

  it('supports automatic first and panel second while preserving the run identity', async () => {
    const completed = {
      ...AUTOMATIC_PENDING, completionStatus: 'COMPLETE', missingSources: [],
      sources: [AUTOMATIC_PENDING.sources[0],
        { source: 'JUDGES', received: true, actor: 'head-judge', receivedAt: '2026-03-02T10:01:00Z' }],
    };
    const fetchMock = mockFetch(response(AUTOMATIC_PENDING), response(completed));
    const user = userEvent.setup();
    render(<ResultsPage />);
    await user.selectOptions(screen.getByLabelText('Tipo de captura'), 'automatic');
    await fillIdentity(user);
    fireEvent.change(screen.getByLabelText('Mediciones'), { target: { value: '{"TIME":70,"ACCURACY":0.8}' } });
    await user.click(screen.getByRole('button', { name: 'Registrar resultado' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Faltan las fuentes: Jueces.');
    await user.selectOptions(screen.getByLabelText('Tipo de captura'), 'panel');
    fireEvent.change(screen.getByLabelText('Evaluaciones'), { target: { value: '[]' } });
    fireEvent.change(screen.getByLabelText('Incidentes'), { target: { value: '[]' } });
    await user.click(screen.getByRole('button', { name: 'Registrar resultado' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Corrida RUN-1 Completa');
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe('/api/runs/automatic-measurements');
    expect((fetchMock.mock.calls[1] as unknown as [string])[0]).toBe('/api/runs/panel-evaluations');
  });

  it('consults a run by id', async () => {
    const fetchMock = mockFetch(response(COMPLETE));
    const user = userEvent.setup();
    render(<ResultsPage />);
    await user.type(screen.getByLabelText('ID de la corrida'), 'RUN/1');
    await user.click(screen.getByRole('button', { name: 'Consultar' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Corrida RUN-1');
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe('/api/runs/RUN%2F1');
  });

  it('shows local format problems and backend conflict messages', async () => {
    const fetchMock = mockFetch(response({ status: 409, error: 'Conflict', message: 'source already registered' }, 409));
    const user = userEvent.setup();
    render(<ResultsPage />);
    await user.click(screen.getByRole('button', { name: 'Registrar resultado' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('El ID de la ronda es obligatorio.');
    expect(fetchMock).not.toHaveBeenCalled();

    await fillIdentity(user);
    await user.click(screen.getByRole('button', { name: 'Registrar resultado' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('source already registered');
    expect(alert).toHaveTextContent('Conflict');
  });
});
