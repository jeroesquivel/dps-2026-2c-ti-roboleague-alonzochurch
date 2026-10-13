import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ScorePage from './ScorePage';
import type { Contribution, RunScore } from './types';

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

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

const TIME: Contribution = {
  ruleCode: 'TIME',
  kind: 'EARNED',
  explanation: '95.500 s against a reference of 120.000 s',
  points: 12.25,
};
const OBJECTIVES: Contribution = {
  ruleCode: 'OBJECTIVES',
  kind: 'EARNED',
  explanation: '4 of 5 objectives at 10 points each',
  points: 40,
};
const RESTART: Contribution = {
  ruleCode: 'PENALTIES',
  kind: 'PENALTY',
  explanation: 'manual restart applied 1 time(s)',
  points: -3,
};

function score(overrides: Partial<RunScore> = {}): RunScore {
  return {
    runId: 'RUN-1',
    teamId: 'TEAM-1',
    challengeId: 'RESCUE',
    rulebookVersion: 'v1',
    completionStatus: 'COMPLETE',
    missingSources: [],
    total: 49.25,
    contributions: [TIME, OBJECTIVES, RESTART],
    bonusCap: null,
    bySource: null,
    ...overrides,
  };
}

async function consult(user: ReturnType<typeof userEvent.setup>, runId = 'RUN-1') {
  await user.type(screen.getByLabelText('ID de la corrida'), runId);
  await user.click(screen.getByRole('button', { name: 'Consultar puntaje' }));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ScorePage', () => {
  it('shows the total, the version and every contribution of a run', async () => {
    const fetchMock = mockFetch(jsonResponse(score(), 200));
    const user = userEvent.setup();
    render(<ScorePage />);

    await consult(user);

    expect(await screen.findByRole('heading', { name: /Puntaje: 49.25/ })).toBeInTheDocument();
    expect(screen.getByText(/Corrida RUN-1/)).toHaveTextContent('reglamento v1');
    expect(screen.getByText(/Puntos TIME: 12.25/)).toHaveTextContent('95.500 s against a reference of 120.000 s');
    expect(screen.getByText(/Puntos OBJECTIVES: 40.00/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe('/runs/RUN-1/score');
    expect(screen.queryByRole('region', { name: 'Tope de bonificaciones' })).not.toBeInTheDocument();
  });

  it('shows a penalty as an identifiable deduction with its explanation', async () => {
    mockFetch(jsonResponse(score(), 200));
    const user = userEvent.setup();
    render(<ScorePage />);

    await consult(user);

    const penalty = await screen.findByText(/Penalidad PENALTIES: -3.00/);
    expect(penalty).toHaveTextContent('manual restart applied 1 time(s)');
    expect(penalty).toHaveClass('contribution--penalty');
  });

  it('shows the bonuses obtained, the cap and the trim when the challenge limits the bonuses', async () => {
    const trim: Contribution = {
      ruleCode: 'BONUS_CAP',
      kind: 'BONUS',
      explanation: 'bonuses obtained 35.00 exceed the cap of 25.00: 10.00 trimmed',
      points: -10,
    };
    mockFetch(
      jsonResponse(
        score({
          total: 75,
          contributions: [OBJECTIVES, { ...TIME, ruleCode: 'BONUS', kind: 'BONUS', points: 35 }, trim],
          bonusCap: { obtained: 35, cap: 25, trimmed: 10, applied: 25 },
        }),
        200,
      ),
    );
    const user = userEvent.setup();
    render(<ScorePage />);

    await consult(user);

    const cap = await screen.findByRole('region', { name: 'Tope de bonificaciones' });
    expect(within(cap).getByText('Bonificaciones obtenidas').nextElementSibling).toHaveTextContent('35.00');
    expect(within(cap).getByText('Tope').nextElementSibling).toHaveTextContent('25.00');
    expect(within(cap).getByText('Recorte').nextElementSibling).toHaveTextContent('10.00');
    expect(within(cap).getByText('Bonificaciones aplicadas').nextElementSibling).toHaveTextContent('25.00');
    expect(screen.getByText(/Bonus BONUS_CAP: -10.00/)).toHaveTextContent('10.00 trimmed');
  });

  it('separates the contributions of a mixed run by source and shows the subtotals', async () => {
    const judges: Contribution = {
      ruleCode: 'JUDGES',
      kind: 'EARNED',
      explanation: 'average of 2 evaluations for CREATIVITY is 7.00 weighted by 2.00',
      points: 14,
    };
    mockFetch(
      jsonResponse(
        score({
          challengeId: 'SHOWCASE',
          total: 49,
          contributions: [TIME, judges, RESTART],
          bySource: {
            sources: [
              { source: 'AUTOMATIC', subtotal: 34, contributions: [TIME] },
              { source: 'JUDGES', subtotal: 15, contributions: [judges, RESTART] },
            ],
            acrossSources: { subtotal: 0, contributions: [] },
          },
        }),
        200,
      ),
    );
    const user = userEvent.setup();
    render(<ScorePage />);

    await consult(user);

    const automatic = await screen.findByRole('region', { name: 'Mediciones automáticas' });
    expect(within(automatic).getByRole('heading')).toHaveTextContent('Mediciones automáticas: 34.00');
    expect(within(automatic).getByText(/Puntos TIME: 12.25/)).toBeInTheDocument();
    const panel = screen.getByRole('region', { name: 'Panel de jueces' });
    expect(within(panel).getByRole('heading')).toHaveTextContent('Panel de jueces: 15.00');
    expect(within(panel).getByText(/Penalidad PENALTIES: -3.00/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Ajustes del desafío' })).not.toBeInTheDocument();
    expect(screen.getAllByText(/Puntos TIME/)).toHaveLength(1);
  });

  it('shows the adjustments that do not belong to a single source', async () => {
    const trim: Contribution = { ruleCode: 'BONUS_CAP', kind: 'BONUS', explanation: 'trimmed', points: -4 };
    mockFetch(
      jsonResponse(
        score({
          bySource: {
            sources: [{ source: 'AUTOMATIC', subtotal: 12.25, contributions: [TIME] }],
            acrossSources: { subtotal: -4, contributions: [trim] },
          },
        }),
        200,
      ),
    );
    const user = userEvent.setup();
    render(<ScorePage />);

    await consult(user);

    const adjustments = await screen.findByRole('region', { name: 'Ajustes del desafío' });
    expect(within(adjustments).getByRole('heading')).toHaveTextContent('Ajustes del desafío: -4.00');
    expect(within(adjustments).getByText(/Bonus BONUS_CAP: -4.00/)).toBeInTheDocument();
  });

  it('shows a pending run without a total and says which sources are missing', async () => {
    mockFetch(
      jsonResponse(
        score({
          completionStatus: 'PENDING',
          missingSources: ['JUDGES'],
          total: null,
          contributions: null,
        }),
        200,
      ),
    );
    const user = userEvent.setup();
    render(<ScorePage />);

    await consult(user);

    expect(await screen.findByRole('heading', { name: 'Puntaje pendiente' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('faltan la evaluación del jurado');
    expect(screen.queryByText(/Puntaje: /)).not.toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  it('names every missing source of a pending run', async () => {
    mockFetch(
      jsonResponse(
        score({ completionStatus: 'PENDING', missingSources: ['AUTOMATIC', 'JUDGES'], total: null, contributions: null }),
        200,
      ),
    );
    const user = userEvent.setup();
    render(<ScorePage />);

    await consult(user);

    expect(await screen.findByRole('status')).toHaveTextContent(
      'faltan las mediciones automáticas y la evaluación del jurado',
    );
  });

  it('shows the error of the API when the run does not exist', async () => {
    mockFetch(jsonResponse({ code: 'NOT_FOUND', message: 'RunResult RUN-9 was not found' }, 404));
    const user = userEvent.setup();
    render(<ScorePage />);

    await consult(user, 'RUN-9');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('RunResult RUN-9 was not found');
    expect(alert).toHaveTextContent('Código: NOT_FOUND');
    expect(screen.queryByRole('region', { name: 'Puntaje' })).not.toBeInTheDocument();
  });

  it('replaces the previous score with the error of a later consultation', async () => {
    mockFetch(
      jsonResponse(score(), 200),
      jsonResponse({ code: 'NOT_FOUND', message: 'RunResult RUN-9 was not found' }, 404),
    );
    const user = userEvent.setup();
    render(<ScorePage />);
    await consult(user);
    await screen.findByRole('heading', { name: /Puntaje: 49.25/ });

    const input = screen.getByLabelText('ID de la corrida');
    await user.clear(input);
    await user.type(input, 'RUN-9');
    await user.click(screen.getByRole('button', { name: 'Consultar puntaje' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('RUN-9 was not found');
    expect(screen.queryByRole('heading', { name: /Puntaje: 49.25/ })).not.toBeInTheDocument();
  });

  it('does not call the API without a run id', async () => {
    const fetchMock = mockFetch();
    const user = userEvent.setup();
    render(<ScorePage />);

    await user.click(screen.getByRole('button', { name: 'Consultar puntaje' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('El ID de la corrida es obligatorio.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows a network failure as an error', async () => {
    mockFetch(new Error('offline'));
    const user = userEvent.setup();
    render(<ScorePage />);

    await consult(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('Código: NETWORK_ERROR');
  });
});
