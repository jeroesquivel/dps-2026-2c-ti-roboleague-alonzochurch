import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import StandingsPage from './StandingsPage';
import type { Standings } from './types';

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

const STANDINGS_URL = '/competitions/COMPETITION-1/categories/CATEGORY-1/standings';

function standings(overrides: Partial<Standings> = {}): Standings {
  return {
    competitionId: 'COMPETITION-1',
    categoryId: 'CATEGORY-1',
    revision: 1,
    status: 'PROVISIONAL',
    generatedAt: '2026-03-02T10:15:30.123Z',
    rulebookVersion: 'v1',
    entries: [
      {
        position: 1,
        teamId: 'TEAM-2',
        teamName: 'Omega Crew',
        totalPoints: 71.5,
        appliedTiebreaks: [{ code: 'HIGHEST_SINGLE_RUN', description: 'highest single run' }],
        explanation: {
          subtotals: [
            {
              code: 'BEST_ROUNDS',
              description: 'best 1 of 2 rounds',
              points: 71.5,
              rounds: [
                { ordinal: 1, points: 60.75, status: 'DISCARDED', reason: 'a better round was available' },
                { ordinal: 2, points: 71.5, status: 'COUNTED', reason: 'among the best 1 rounds' },
              ],
            },
          ],
        },
      },
      {
        position: 2,
        teamId: 'TEAM-1',
        teamName: 'Delta Bots',
        totalPoints: 60.75,
        appliedTiebreaks: [],
        explanation: { subtotals: [{ code: 'STATED_TOTAL', description: 'total', points: 60.75, rounds: [] }] },
      },
    ],
    pendingRuns: [],
    ...overrides,
  };
}

const PENDING_RUN = {
  runId: 'RUN-9',
  teamId: 'TEAM-3',
  teamName: 'Sigma Works',
  roundId: 'ROUND-6',
  attemptNumber: 1,
  missingSources: ['JUDGES'],
};

async function fillCategory(user: UserSession, actor = 'organizer') {
  await user.type(screen.getByLabelText('ID de la competencia'), 'COMPETITION-1');
  await user.type(screen.getByLabelText('ID de la categoría'), 'CATEGORY-1');
  if (actor) {
    await user.type(screen.getByLabelText('Operación realizada por'), actor);
  }
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('StandingsPage', () => {
  it('shows the latest revision in the order given by the API', async () => {
    const fetchMock = mockFetch(jsonResponse(standings(), 200));
    const user = userEvent.setup();
    render(<StandingsPage />);

    await fillCategory(user, '');
    await user.click(screen.getByRole('button', { name: 'Consultar posiciones' }));

    expect(await screen.findByRole('heading', { name: /Posiciones, revisión 1/ })).toHaveTextContent('Provisional');
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('1Omega Crew (TEAM-2)71.50highest single run');
    expect(rows[1]).toHaveTextContent('2Delta Bots (TEAM-1)60.75—');
    expect(screen.getByText(/Reglamento v1/)).toHaveTextContent('2026-03-02 10:15 UTC');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe(STANDINGS_URL);
  });

  it('explains which rounds counted and which were discarded', async () => {
    mockFetch(jsonResponse(standings(), 200));
    const user = userEvent.setup();
    render(<StandingsPage />);
    await fillCategory(user, '');
    await user.click(screen.getByRole('button', { name: 'Consultar posiciones' }));

    await user.click(await screen.findByText('Ver explicación de Omega Crew'));

    expect(screen.getByText(/Ronda 1: 60.75 \(Descartada\)/)).toHaveTextContent('a better round was available');
    expect(screen.getByText(/Ronda 2: 71.50 \(Computada\)/)).toHaveTextContent('among the best 1 rounds');
  });

  it('lists the pending runs that do not score', async () => {
    mockFetch(jsonResponse(standings({ pendingRuns: [PENDING_RUN] }), 200));
    const user = userEvent.setup();
    render(<StandingsPage />);
    await fillCategory(user, '');
    await user.click(screen.getByRole('button', { name: 'Consultar posiciones' }));

    const pending = await screen.findByRole('region', { name: 'Corridas pendientes' });
    expect(pending).toHaveTextContent('Sigma Works');
    expect(pending).toHaveTextContent('ROUND-6');
    expect(pending).toHaveTextContent('falta la evaluación del jurado');
  });

  it('generates the standings as the given actor', async () => {
    const fetchMock = mockFetch(jsonResponse(standings(), 201));
    const user = userEvent.setup();
    render(<StandingsPage />);

    await fillCategory(user);
    await user.click(screen.getByRole('button', { name: 'Generar posiciones' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Se generó la revisión 1');
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(STANDINGS_URL);
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ actor: 'organizer' });
    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('does not call the API when the category or the actor is missing', async () => {
    const fetchMock = mockFetch();
    const user = userEvent.setup();
    render(<StandingsPage />);

    await user.click(screen.getByRole('button', { name: 'Generar posiciones' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('El ID de la competencia es obligatorio.');
    expect(alert).toHaveTextContent('El ID de la categoría es obligatorio.');
    expect(alert).toHaveTextContent('Indicá quién realiza la operación.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('publishes a provisional revision and then offers nothing more to publish', async () => {
    const fetchMock = mockFetch(
      jsonResponse(standings(), 200),
      jsonResponse(standings({ status: 'FINAL' }), 200),
    );
    const user = userEvent.setup();
    render(<StandingsPage />);
    await fillCategory(user);
    await user.click(screen.getByRole('button', { name: 'Consultar posiciones' }));

    await user.click(await screen.findByRole('button', { name: 'Publicar como final' }));

    expect(await screen.findByRole('status')).toHaveTextContent('La revisión 1 quedó publicada como final');
    expect(screen.getByRole('heading', { name: /Posiciones, revisión 1/ })).toHaveTextContent('Final');
    expect(screen.queryByRole('button', { name: 'Publicar como final' })).not.toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[1] as unknown as [string, RequestInit];
    expect(url).toBe(`${STANDINGS_URL}/publication`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ actor: 'organizer' });
  });

  it('keeps the revision provisional and shows the conflict when the publication is refused', async () => {
    mockFetch(
      jsonResponse(standings({ pendingRuns: [PENDING_RUN] }), 200),
      jsonResponse({ code: 'CONFLICT', message: '1 runs are still pending: 1 waiting for JUDGES' }, 409),
    );
    const user = userEvent.setup();
    render(<StandingsPage />);
    await fillCategory(user);
    await user.click(screen.getByRole('button', { name: 'Consultar posiciones' }));

    await user.click(await screen.findByRole('button', { name: 'Publicar como final' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('1 runs are still pending: 1 waiting for JUDGES');
    expect(alert).toHaveTextContent('CONFLICT');
    expect(screen.getByRole('heading', { name: /Posiciones, revisión 1/ })).toHaveTextContent('Provisional');
    expect(screen.getByRole('button', { name: 'Publicar como final' })).toBeInTheDocument();
  });

  it('does not offer to publish a final revision', async () => {
    mockFetch(jsonResponse(standings({ status: 'FINAL' }), 200));
    const user = userEvent.setup();
    render(<StandingsPage />);
    await fillCategory(user, '');

    await user.click(screen.getByRole('button', { name: 'Consultar posiciones' }));

    expect(await screen.findByRole('heading', { name: /Posiciones, revisión 1/ })).toHaveTextContent('Final');
    expect(screen.queryByRole('button', { name: 'Publicar como final' })).not.toBeInTheDocument();
  });

  it('shows every revision and lets the user look at an earlier one', async () => {
    const first = standings({ status: 'FINAL' });
    const second = standings({
      revision: 2,
      status: 'PROVISIONAL',
      generatedAt: '2026-03-02T11:00:00Z',
      entries: [standings().entries[1]],
    });
    const fetchMock = mockFetch(jsonResponse(second, 200), jsonResponse({ revisions: [first, second] }, 200));
    const user = userEvent.setup();
    render(<StandingsPage />);
    await fillCategory(user, '');
    await user.click(screen.getByRole('button', { name: 'Consultar posiciones' }));
    await screen.findByRole('heading', { name: /Posiciones, revisión 2/ });

    await user.click(screen.getByRole('button', { name: 'Ver historial' }));

    const revisions = await screen.findByRole('navigation', { name: 'Revisiones' });
    expect(within(revisions).getAllByRole('button')).toHaveLength(2);
    expect(within(revisions).getByRole('button', { name: /Revisión 2/ })).toHaveAttribute('aria-current', 'true');
    expect((fetchMock.mock.calls[1] as unknown as [string])[0]).toBe(`${STANDINGS_URL}/revisions`);

    await user.click(within(revisions).getByRole('button', { name: /Revisión 1 · Final/ }));

    expect(screen.getByRole('heading', { name: /Posiciones, revisión 1/ })).toHaveTextContent('Final');
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(3);
    expect(screen.queryByRole('button', { name: 'Publicar como final' })).not.toBeInTheDocument();
  });

  it('shows the API error when the category has no standings', async () => {
    mockFetch(jsonResponse({ code: 'NOT_FOUND', message: 'Standings CATEGORY-1 was not found' }, 404));
    const user = userEvent.setup();
    render(<StandingsPage />);
    await fillCategory(user, '');

    await user.click(screen.getByRole('button', { name: 'Consultar posiciones' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Standings CATEGORY-1 was not found');
    expect(alert).toHaveTextContent('NOT_FOUND');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('reports a network failure', async () => {
    mockFetch(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    render(<StandingsPage />);
    await fillCategory(user, '');

    await user.click(screen.getByRole('button', { name: 'Consultar posiciones' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor.');
  });

  it('says so when no team has scoreable results yet', async () => {
    mockFetch(jsonResponse(standings({ entries: [], pendingRuns: [PENDING_RUN] }), 201));
    const user = userEvent.setup();
    render(<StandingsPage />);
    await fillCategory(user);

    await user.click(screen.getByRole('button', { name: 'Generar posiciones' }));

    expect(await screen.findByText('Todavía no hay equipos con resultados puntuables.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });
});
