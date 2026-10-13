import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ScheduleRoundForm from './ScheduleRoundForm';

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

const ROUND = {
  roundId: 'round-1',
  competitionId: 'competition-1',
  categoryId: 'category-1',
  challengeId: 'RESCUE',
  ordinal: 1,
  rulebookVersion: 'v1',
  heats: [
    { heatId: 'heat-1', teamId: 'team-alpha', arenaId: 'A1', start: '2026-03-02T10:00:00',
      end: '2026-03-02T10:05:00', durationMinutes: 5, judges: ['judge-1', 'judge-2'] },
  ],
};

async function fillHeat(user: UserSession, heat: ReturnType<typeof within>, team: string, start: string) {
  await user.type(heat.getByLabelText('ID del equipo'), team);
  await user.type(heat.getByLabelText('ID de la pista'), 'A1');
  fireEvent.change(heat.getByLabelText('Inicio'), { target: { value: start } });
  await user.type(heat.getByLabelText('Duración (minutos)'), '5');
  await user.type(heat.getByLabelText('Jueces (separados por coma)'), 'judge-1, judge-2');
}

async function fillValidForm(user: UserSession) {
  await user.type(screen.getByLabelText('ID de la competencia'), 'competition-1');
  await user.type(screen.getByLabelText('ID de la categoría'), 'category-1');
  await user.type(screen.getByLabelText('ID del desafío'), 'RESCUE');
  await user.type(screen.getByLabelText('Número de ronda'), '1');
  await user.type(screen.getByLabelText('Programada por'), 'organizer');
  await fillHeat(user, within(screen.getByRole('group', { name: 'Largada 1' })), 'team-alpha', '2026-03-02T10:00');
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ScheduleRoundForm', () => {
  it('schedules the round and shows its detail', async () => {
    const fetchMock = mockFetch(jsonResponse({ roundId: 'round-1' }, 201), jsonResponse(ROUND, 200));
    const user = userEvent.setup();
    render(<ScheduleRoundForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Programar ronda' }));

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('Ronda programada');
    expect(status).toHaveTextContent('round-1');
    expect(status).toHaveTextContent('v1');
    const row = within(status).getByRole('row', { name: /team-alpha/ });
    expect(row).toHaveTextContent('2026-03-02 10:00');
    expect(row).toHaveTextContent('2026-03-02 10:05');
    expect(row).toHaveTextContent('5 min');
    expect(row).toHaveTextContent('judge-1, judge-2');

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/rounds');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({
      competitionId: 'competition-1',
      categoryId: 'category-1',
      challengeId: 'RESCUE',
      ordinal: 1,
      heats: [
        { teamId: 'team-alpha', arenaId: 'A1', start: '2026-03-02T10:00:00', durationMinutes: 5,
          judges: ['judge-1', 'judge-2'] },
      ],
      actor: 'organizer',
    });
    expect((fetchMock.mock.calls[1] as unknown as [string])[0]).toBe('/rounds/round-1');
  });

  it('lists each schedule conflict with its type', async () => {
    mockFetch(
      jsonResponse(
        {
          code: 'SCHEDULE_CONFLICT',
          message: 'heat conflicts with the booked schedule',
          conflicts: [
            { type: 'ARENA_BUSY', detail: 'arena A1 is busy from 10:00 to 10:05' },
            { type: 'JUDGE_BUSY', detail: 'judge judge-1 is already assigned' },
          ],
        },
        409,
      ),
    );
    const user = userEvent.setup();
    render(<ScheduleRoundForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Programar ronda' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Conflicto de agenda');
    expect(alert).toHaveTextContent('Pista ocupada: arena A1 is busy from 10:00 to 10:05');
    expect(alert).toHaveTextContent('Juez ocupado: judge judge-1 is already assigned');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('shows the message and code of other API errors', async () => {
    mockFetch(jsonResponse({ code: 'NOT_FOUND', message: 'team team-alpha was not found' }, 404));
    const user = userEvent.setup();
    render(<ScheduleRoundForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Programar ronda' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('No se pudo programar la ronda');
    expect(alert).toHaveTextContent('team team-alpha was not found');
    expect(alert).toHaveTextContent('NOT_FOUND');
  });

  it('reports a network failure', async () => {
    mockFetch(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    render(<ScheduleRoundForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Programar ronda' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor.');
  });

  it('still confirms the round when its detail cannot be loaded', async () => {
    mockFetch(jsonResponse({ roundId: 'round-1' }, 201), new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    render(<ScheduleRoundForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Programar ronda' }));

    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('round-1');
    expect(status).toHaveTextContent('no se pudo cargar su detalle');
  });

  it('does not call the API when required data is missing', async () => {
    const fetchMock = mockFetch();
    const user = userEvent.setup();
    render(<ScheduleRoundForm />);

    await user.click(screen.getByRole('button', { name: 'Programar ronda' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('El ID de la competencia es obligatorio.');
    expect(alert).toHaveTextContent('El número de ronda debe ser un entero mayor o igual a 1.');
    expect(alert).toHaveTextContent('Largada 1: se requiere al menos un juez.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('adds and removes heats', async () => {
    const fetchMock = mockFetch(jsonResponse({ roundId: 'round-1' }, 201), jsonResponse(ROUND, 200));
    const user = userEvent.setup();
    render(<ScheduleRoundForm />);

    expect(screen.queryByRole('button', { name: /Quitar largada/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Agregar largada' }));
    await user.click(screen.getByRole('button', { name: 'Agregar largada' }));
    expect(screen.getAllByRole('group', { name: /Largada/ })).toHaveLength(3);

    await user.click(screen.getByRole('button', { name: 'Quitar largada 3' }));
    expect(screen.getAllByRole('group', { name: /Largada/ })).toHaveLength(2);

    await fillValidForm(user);
    await fillHeat(user, within(screen.getByRole('group', { name: 'Largada 2' })), 'team-bravo', '2026-03-02T10:05');
    await user.click(screen.getByRole('button', { name: 'Programar ronda' }));

    await screen.findByRole('status');
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    const heats = JSON.parse(init.body as string).heats as { teamId: string; start: string }[];
    expect(heats.map((heat) => heat.teamId)).toEqual(['team-alpha', 'team-bravo']);
    expect(heats[1].start).toBe('2026-03-02T10:05:00');
  });
});
