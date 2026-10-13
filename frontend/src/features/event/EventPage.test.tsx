import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EventPage from './EventPage';
import { RULEBOOK_PRESETS } from './presets';
import type { Competition } from './types';

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function mockFetch(...responses: (Response | Error)[]) {
  const queue = [...responses];
  const fetchMock = vi.fn((_url: string, _init?: RequestInit) => {
    const next = queue.shift();
    if (next === undefined) {
      return Promise.reject(new Error('unexpected fetch'));
    }
    return next instanceof Error ? Promise.reject(next) : Promise.resolve(next);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function call(fetchMock: ReturnType<typeof mockFetch>, index: number): { url: string; init?: RequestInit } {
  const [url, init] = fetchMock.mock.calls[index] as unknown as [string, RequestInit | undefined];
  return { url, init };
}

function bodyOf(fetchMock: ReturnType<typeof mockFetch>, index: number): Record<string, unknown> {
  return JSON.parse(call(fetchMock, index).init?.body as string) as Record<string, unknown>;
}

const COMPETITION: Competition = {
  competitionId: 'COMPETITION-1',
  name: 'National Open',
  period: { start: '2027-05-10', end: '2027-05-12' },
  rulebookVersion: 'v1',
  categories: [
    { categoryId: 'CATEGORY-1', name: 'Junior', minimumAge: 12, maximumAge: 17, robotClass: 'RESCUE_BOT' },
    { categoryId: 'CATEGORY-2', name: 'Senior', minimumAge: 18, maximumAge: 30, robotClass: 'SUMO' },
  ],
};

async function fillSeason(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Nombre de la temporada'), 'Season 2027');
  await user.type(screen.getByLabelText('Año'), '2027');
  fireEvent.change(screen.getByLabelText('Inicio de la temporada'), { target: { value: '2027-03-01' } });
  fireEvent.change(screen.getByLabelText('Fin de la temporada'), { target: { value: '2027-11-30' } });
}

async function fillCategory(user: ReturnType<typeof userEvent.setup>, fieldset: HTMLElement, values: string[]) {
  const [name, min, max, robotClass] = values;
  await user.type(within(fieldset).getByLabelText('Nombre de la categoría'), name);
  await user.type(within(fieldset).getByLabelText('Edad mínima'), min);
  await user.type(within(fieldset).getByLabelText('Edad máxima'), max);
  await user.type(within(fieldset).getByLabelText('Clase de robot'), robotClass);
}

async function fillCompetition(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Nombre de la competencia'), 'National Open');
  fireEvent.change(screen.getByLabelText('Inicio de la competencia'), { target: { value: '2027-05-10' } });
  fireEvent.change(screen.getByLabelText('Fin de la competencia'), { target: { value: '2027-05-12' } });
  await fillCategory(user, screen.getByRole('group', { name: 'Categoría 1' }), ['Junior', '12', '17', 'RESCUE_BOT']);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('EventPage', () => {
  it('walks the season, the competition and its consultation without retyping any obtained id', async () => {
    const fetchMock = mockFetch(
      jsonResponse({ seasonId: 'SEASON-1' }, 201),
      jsonResponse(
        {
          competitionId: 'COMPETITION-1',
          rulebookVersion: 'v1',
          categories: [
            { categoryId: 'CATEGORY-1', name: 'Junior' },
            { categoryId: 'CATEGORY-2', name: 'Senior' },
          ],
        },
        201,
      ),
      jsonResponse(COMPETITION, 200),
    );
    const user = userEvent.setup();
    render(<EventPage />);

    await fillSeason(user);
    await user.click(screen.getByRole('button', { name: 'Crear temporada' }));

    expect(await screen.findByText('SEASON-1')).toBeInTheDocument();
    expect(call(fetchMock, 0).url).toBe('/seasons');
    expect(bodyOf(fetchMock, 0)).toEqual({
      name: 'Season 2027',
      year: 2027,
      period: { start: '2027-03-01', end: '2027-11-30' },
      actor: 'organizer',
    });
    // el paso siguiente ya trae la temporada obtenida
    expect(screen.getByLabelText('ID de la temporada')).toHaveValue('SEASON-1');

    await fillCompetition(user);
    await user.click(screen.getByRole('button', { name: 'Agregar categoría' }));
    await fillCategory(user, screen.getByRole('group', { name: 'Categoría 2' }), ['Senior', '18', '30', 'SUMO']);
    await user.click(screen.getByRole('button', { name: 'Crear competencia' }));

    const created = await screen.findByRole('table', { name: 'Categorías creadas' });
    expect(within(created).getAllByRole('row').map((row) => row.textContent)).toEqual([
      'CategoríaID',
      'JuniorCATEGORY-1',
      'SeniorCATEGORY-2',
    ]);
    expect(call(fetchMock, 1).url).toBe('/seasons/SEASON-1/competitions');
    const sent = bodyOf(fetchMock, 1);
    expect(sent.name).toBe('National Open');
    expect(sent.period).toEqual({ start: '2027-05-10', end: '2027-05-12' });
    expect(sent.categories).toEqual([
      { name: 'Junior', minimumAge: 12, maximumAge: 17, robotClass: 'RESCUE_BOT' },
      { name: 'Senior', minimumAge: 18, maximumAge: 30, robotClass: 'SUMO' },
    ]);
    expect(sent.rulebook).toEqual(RULEBOOK_PRESETS[0].rulebook);
    expect(sent.actor).toBe('organizer');

    // la competencia creada se consulta sola y se muestra con su versión activa
    const consulted = await screen.findByRole('region', { name: 'Competencia consultada' });
    expect(call(fetchMock, 2).url).toBe('/competitions/COMPETITION-1');
    expect(within(consulted).getByText('Reglamento activo').nextElementSibling).toHaveTextContent('v1');
    expect(within(consulted).getByText('2027-05-10 a 2027-05-12')).toBeInTheDocument();
    expect(within(consulted).getByRole('cell', { name: 'RESCUE_BOT' })).toBeInTheDocument();
    expect(screen.getByLabelText('ID de la competencia')).toHaveValue('COMPETITION-1');
  });

  it('publishes a new version with an example rulebook and shows it as the active one', async () => {
    const fetchMock = mockFetch(
      jsonResponse(COMPETITION, 200),
      jsonResponse({ competitionId: 'COMPETITION-1', rulebookVersion: 'v2' }, 201),
      jsonResponse({ ...COMPETITION, rulebookVersion: 'v2' }, 200),
    );
    const user = userEvent.setup();
    render(<EventPage />);

    await user.type(screen.getByLabelText('ID de la competencia'), 'COMPETITION-1');
    await user.click(screen.getByRole('button', { name: 'Consultar competencia' }));
    await screen.findByRole('region', { name: 'Competencia consultada' });
    await user.selectOptions(screen.getByLabelText('Nuevo reglamento: ejemplo'), 'mixed');
    await user.click(screen.getByRole('button', { name: 'Publicar nueva versión' }));

    expect(await screen.findByText('v2', { selector: 'strong' })).toBeInTheDocument();
    expect(call(fetchMock, 1).url).toBe('/competitions/COMPETITION-1/rulebooks');
    const sent = bodyOf(fetchMock, 1);
    expect(sent.rulebook).toEqual(RULEBOOK_PRESETS[1].rulebook);
    expect(sent.actor).toBe('organizer');
    const consulted = screen.getByRole('region', { name: 'Competencia consultada' });
    expect(within(consulted).getByText('Reglamento activo').nextElementSibling).toHaveTextContent('v2');
  });

  it('sends the rulebook exactly as the user edited it', async () => {
    const fetchMock = mockFetch(jsonResponse({ competitionId: 'COMPETITION-1', rulebookVersion: 'v2' }, 201),
      jsonResponse(COMPETITION, 200));
    const user = userEvent.setup();
    render(<EventPage />);

    await user.type(screen.getByLabelText('ID de la competencia'), 'COMPETITION-1');
    fireEvent.change(screen.getByLabelText('Nuevo reglamento (JSON)'), {
      target: { value: '{"challenges":[],"appealWindowMinutes":5}' },
    });
    await user.click(screen.getByRole('button', { name: 'Publicar nueva versión' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(bodyOf(fetchMock, 0).rulebook).toEqual({ challenges: [], appealWindowMinutes: 5 });
  });

  it('checks presence and format before sending anything', async () => {
    const fetchMock = mockFetch();
    const user = userEvent.setup();
    render(<EventPage />);

    await user.click(screen.getByRole('button', { name: 'Crear temporada' }));
    const season = screen.getByRole('region', { name: 'Temporada' });
    expect(within(season).getByRole('alert')).toHaveTextContent('El nombre de la temporada es obligatorio.');
    expect(within(season).getByRole('alert')).toHaveTextContent('El año debe ser un número entero.');

    await user.click(screen.getByRole('button', { name: 'Crear competencia' }));
    const competition = screen.getByRole('region', { name: 'Competencia' });
    const alert = within(competition).getByRole('alert');
    expect(alert).toHaveTextContent('El ID de la temporada es obligatorio.');
    expect(alert).toHaveTextContent('Categoría 1: el nombre es obligatorio.');
    expect(alert).toHaveTextContent('Categoría 1: las edades mínima y máxima deben ser números enteros.');

    await user.click(screen.getByRole('button', { name: 'Publicar nueva versión' }));
    expect(screen.getByText('El ID de la competencia es obligatorio.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refuses a rulebook that is not a JSON object without calling the API', async () => {
    const fetchMock = mockFetch();
    const user = userEvent.setup();
    render(<EventPage />);
    await user.type(screen.getByLabelText('ID de la competencia'), 'COMPETITION-1');

    for (const [text, message] of [
      ['{"challenges": [', 'El reglamento no es un JSON válido.'],
      ['[1, 2]', 'El reglamento debe ser un objeto JSON.'],
      ['', 'El reglamento es obligatorio.'],
    ]) {
      fireEvent.change(screen.getByLabelText('Nuevo reglamento (JSON)'), { target: { value: text } });
      await user.click(screen.getByRole('button', { name: 'Publicar nueva versión' }));
      expect(screen.getByText(message)).toBeInTheDocument();
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('adds and removes categories', async () => {
    const user = userEvent.setup();
    render(<EventPage />);

    expect(screen.queryByRole('button', { name: /Quitar categoría/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Agregar categoría' }));
    expect(screen.getByRole('group', { name: 'Categoría 2' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Quitar categoría 1' }));

    expect(screen.queryByRole('group', { name: 'Categoría 2' })).not.toBeInTheDocument();
    expect(screen.getByRole('group', { name: 'Categoría 1' })).toBeInTheDocument();
  });

  it('shows the API error of each step with its code', async () => {
    mockFetch(
      jsonResponse({ code: 'CONFLICT', message: 'season period overlaps season Season 2027' }, 409),
      jsonResponse({ code: 'NOT_FOUND', message: 'Season SEASON-9 was not found' }, 404),
      jsonResponse({ code: 'RULE_VIOLATION', message: 'competition period is outside season' }, 422),
      jsonResponse({ code: 'NOT_FOUND', message: 'Competition COMPETITION-9 was not found' }, 404),
    );
    const user = userEvent.setup();
    render(<EventPage />);

    await fillSeason(user);
    await user.click(screen.getByRole('button', { name: 'Crear temporada' }));
    expect(await screen.findByText('season period overlaps season Season 2027')).toBeInTheDocument();
    expect(screen.getByText('Código: CONFLICT')).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('ID de la temporada'), 'SEASON-9');
    await fillCompetition(user);
    await user.click(screen.getByRole('button', { name: 'Crear competencia' }));
    expect(await screen.findByText('Season SEASON-9 was not found')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Crear competencia' }));
    expect(await screen.findByText('competition period is outside season')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Categorías creadas' })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('ID de la competencia'), 'COMPETITION-9');
    await user.click(screen.getByRole('button', { name: 'Consultar competencia' }));
    expect(await screen.findByText('Competition COMPETITION-9 was not found')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Competencia consultada' })).not.toBeInTheDocument();
  });

  it('reports a rejected publication and keeps the version that was active', async () => {
    mockFetch(
      jsonResponse(COMPETITION, 200),
      jsonResponse({ code: 'INVALID_VALUE', message: 'rulebook challenges is required' }, 400),
    );
    const user = userEvent.setup();
    render(<EventPage />);

    await user.type(screen.getByLabelText('ID de la competencia'), 'COMPETITION-1');
    await user.click(screen.getByRole('button', { name: 'Consultar competencia' }));
    await screen.findByRole('region', { name: 'Competencia consultada' });
    await user.click(screen.getByRole('button', { name: 'Publicar nueva versión' }));

    expect(await screen.findByText('rulebook challenges is required')).toBeInTheDocument();
    expect(screen.queryByText(/Versión publicada/)).not.toBeInTheDocument();
    const consulted = screen.getByRole('region', { name: 'Competencia consultada' });
    expect(within(consulted).getByText('Reglamento activo').nextElementSibling).toHaveTextContent('v1');
  });

  it('reports an unexpected failure with a generic message', async () => {
    mockFetch(new Error('offline'));
    const user = userEvent.setup();
    render(<EventPage />);

    await fillSeason(user);
    await user.click(screen.getByRole('button', { name: 'Crear temporada' }));

    expect(await screen.findByText('No se pudo conectar con el servidor.')).toBeInTheDocument();
  });

  it('lets the actor of the configuration be changed', async () => {
    const fetchMock = mockFetch(jsonResponse({ seasonId: 'SEASON-1' }, 201));
    const user = userEvent.setup();
    render(<EventPage />);

    await user.clear(screen.getByLabelText('Actor de la configuración'));
    await user.type(screen.getByLabelText('Actor de la configuración'), 'jefe de pista');
    await fillSeason(user);
    await user.click(screen.getByRole('button', { name: 'Crear temporada' }));

    await screen.findByText('SEASON-1');
    expect(bodyOf(fetchMock, 0).actor).toBe('jefe de pista');
  });
});
