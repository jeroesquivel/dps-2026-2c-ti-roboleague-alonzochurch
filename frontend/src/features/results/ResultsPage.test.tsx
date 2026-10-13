import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ResultsPage from './ResultsPage';

const COMPLETE = {
  runId: 'RUN-1', roundId: 'ROUND-1', heatId: 'HEAT-1', teamId: 'TEAM-1', challengeId: 'RESCUE',
  rulebookVersion: 'v1', attemptNumber: 1, capturedAt: '2026-03-02T10:00:00Z', status: 'CAPTURED',
  currentMeasurements: { TIME: 70 }, currentIncidents: [{ code: 'RESTART', occurrences: 1 }],
  evaluations: [{ judgeId: 'J1', criterion: 'DESIGN', score: 8 }],
  originalMeasurements: { TIME: 70 }, originalIncidents: [{ code: 'RESTART', occurrences: 1 }],
  corrections: [],
  completion: { status: 'COMPLETE', expectedSources: [], missingSources: [], receipts: [] },
};

const AUTOMATIC_PENDING = {
  ...COMPLETE, challengeId: 'SHOWCASE', evaluations: [], currentIncidents: [], originalIncidents: [],
  completion: {
    status: 'PENDING', expectedSources: ['AUTOMATIC', 'JUDGES'], missingSources: ['JUDGES'],
    receipts: [{ source: 'AUTOMATIC', actor: 'track-system', receivedAt: '2026-03-02T10:00:00Z' }],
  },
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
    expect(status).toHaveTextContent('Capturada en una sola operación.');
    expect(status).toHaveTextContent('J1 · DESIGN: 8');
    expect(status).toHaveTextContent('RESTART × 1');
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/runs');
    expect(JSON.parse(init.body as string)).toMatchObject({
      roundId: 'ROUND-1', teamId: 'TEAM-1', attemptNumber: 1, actor: 'scorekeeper',
      measurements: { TIME: 70 },
    });
  });

  it('supports automatic first and panel second while preserving the run identity', async () => {
    const completed = {
      ...AUTOMATIC_PENDING,
      completion: {
        status: 'COMPLETE', expectedSources: ['AUTOMATIC', 'JUDGES'], missingSources: [],
        receipts: [...AUTOMATIC_PENDING.completion.receipts,
          { source: 'JUDGES', actor: 'head-judge', receivedAt: '2026-03-02T10:01:00Z' }],
      },
    };
    const fetchMock = mockFetch(response(AUTOMATIC_PENDING), response(completed));
    const user = userEvent.setup();
    render(<ResultsPage />);
    await user.selectOptions(screen.getByLabelText('Tipo de captura'), 'automatic');
    await fillIdentity(user);
    fireEvent.change(screen.getByLabelText('Mediciones'), { target: { value: '{"TIME":70,"ACCURACY":0.8}' } });
    await user.click(screen.getByRole('button', { name: 'Registrar resultado' }));

    const pending = await screen.findByRole('status');
    expect(pending).toHaveTextContent('Faltan las fuentes: Jueces.');
    expect(pending).toHaveTextContent('Automática: recibida por track-system');
    expect(pending).toHaveTextContent('Jueces: no recibida');
    await user.selectOptions(screen.getByLabelText('Tipo de captura'), 'panel');
    fireEvent.change(screen.getByLabelText('Evaluaciones'), { target: { value: '[]' } });
    fireEvent.change(screen.getByLabelText('Incidentes'), { target: { value: '[]' } });
    await user.click(screen.getByRole('button', { name: 'Registrar resultado' }));

    const done = await screen.findByRole('status');
    expect(done).toHaveTextContent('Corrida RUN-1 Completa');
    expect(done).toHaveTextContent('Jueces: recibida por head-judge');
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe('/runs/automatic-measurements');
    expect((fetchMock.mock.calls[1] as unknown as [string])[0]).toBe('/runs/panel-evaluations');
  });

  it('consults a run by id', async () => {
    const fetchMock = mockFetch(response(COMPLETE));
    const user = userEvent.setup();
    render(<ResultsPage />);
    await user.type(screen.getByLabelText('ID de la corrida'), 'RUN/1');
    await user.click(screen.getByRole('button', { name: 'Consultar' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Corrida RUN-1');
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe('/runs/RUN%2F1');
  });

  it('shows local format problems and backend conflict messages', async () => {
    const fetchMock = mockFetch(response({ code: 'CONFLICT', message: 'source already registered' }, 409));
    const user = userEvent.setup();
    render(<ResultsPage />);
    await user.click(screen.getByRole('button', { name: 'Registrar resultado' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('El ID de la ronda es obligatorio.');
    expect(fetchMock).not.toHaveBeenCalled();

    await fillIdentity(user);
    await user.click(screen.getByRole('button', { name: 'Registrar resultado' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('source already registered');
    expect(alert).toHaveTextContent('CONFLICT');
  });
});
