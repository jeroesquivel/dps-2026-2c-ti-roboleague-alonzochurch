import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import RegisterTeamForm from './RegisterTeamForm';

type UserSession = ReturnType<typeof userEvent.setup>;

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function mockFetch(response: Response | Error) {
  const fetchMock = vi.fn(() => (response instanceof Error ? Promise.reject(response) : Promise.resolve(response)));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function fillValidForm(user: UserSession) {
  await user.type(screen.getByLabelText('ID de la competencia'), 'competition-1');
  await user.type(screen.getByLabelText('ID de la categoría'), 'category-1');
  await user.type(screen.getByLabelText('Nombre del equipo'), 'Rescue Crew');
  await user.type(screen.getByLabelText('Registrado por'), 'organizer');
  const member = within(screen.getByRole('group', { name: 'Integrante 1' }));
  await user.type(member.getByLabelText('Nombre completo'), 'Ada');
  fireEvent.change(member.getByLabelText('Fecha de nacimiento'), { target: { value: '2010-05-20' } });
  await user.type(screen.getByLabelText('Nombre del robot'), 'Rescuer');
  await user.type(screen.getByLabelText('Clase del robot'), 'RESCUE_BOT');
  await user.type(screen.getByLabelText('Peso (kg)'), '2.4');
  await user.type(screen.getByLabelText('Largo (mm)'), '180');
  await user.type(screen.getByLabelText('Ancho (mm)'), '180');
  await user.type(screen.getByLabelText('Alto (mm)'), '150');
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('RegisterTeamForm', () => {
  it('sends the registration and shows an accepted team', async () => {
    const fetchMock = mockFetch(jsonResponse({ teamId: 'team-1', status: 'ACCEPTED', violations: [] }, 201));
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Agregar documento' }));
    const document = within(screen.getByRole('group', { name: 'Documento 1' }));
    await user.type(document.getByLabelText('Referencia'), 'PC-1');
    await user.click(screen.getByRole('button', { name: 'Registrar equipo' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Inscripción aceptada');
    expect(screen.getByRole('status')).toHaveTextContent('team-1');
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/teams');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({
      competitionId: 'competition-1',
      categoryId: 'category-1',
      teamName: 'Rescue Crew',
      members: [{ fullName: 'Ada', birthDate: '2010-05-20', role: 'COMPETITOR' }],
      robot: { name: 'Rescuer', robotClass: 'RESCUE_BOT', weightKg: 2.4,
        dimensions: { lengthMm: 180, widthMm: 180, heightMm: 150 } },
      documents: [{ type: 'PARENTAL_CONSENT', reference: 'PC-1' }],
      actor: 'organizer',
    });
  });

  it('shows a rejected team with every violation returned by the API', async () => {
    mockFetch(jsonResponse({
      teamId: 'team-2',
      status: 'REJECTED',
      violations: [
        { code: 'AGE_RANGE', reason: 'Tim is 8 years old and category Junior admits ages 12 to 17' },
        { code: 'REQUIRED_DOCUMENTS', reason: 'missing document TECHNICAL_SHEET' },
        { code: 'SOME_FUTURE_RULE', reason: 'a rule the UI does not know yet' },
      ],
    }, 201));
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrar equipo' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Inscripción rechazada');
    expect(alert).toHaveTextContent('team-2');
    const items = within(alert).getAllByRole('listitem');
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveTextContent('Edad de los integrantes: Tim is 8 years old');
    expect(items[1]).toHaveTextContent('Documentación requerida: missing document TECHNICAL_SHEET');
    expect(items[2]).toHaveTextContent('SOME_FUTURE_RULE: a rule the UI does not know yet');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('shows the business error returned by the API with its code', async () => {
    mockFetch(jsonResponse({ code: 'NOT_FOUND', message: 'Competition competition-1 was not found' }, 404));
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrar equipo' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('No se pudo registrar el equipo');
    expect(alert).toHaveTextContent('Competition competition-1 was not found');
    expect(alert).toHaveTextContent('NOT_FOUND');
  });

  it('shows a connection error when the server cannot be reached', async () => {
    mockFetch(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrar equipo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor.');
  });

  it('reports an unexpected response that is not an API error', async () => {
    mockFetch(new Response('<html>boom</html>', { status: 500 }));
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrar equipo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Respuesta inesperada del servidor (HTTP 500).');
  });

  it('reports missing data and does not call the API', async () => {
    const fetchMock = mockFetch(jsonResponse({}, 201));
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    await user.click(screen.getByRole('button', { name: 'Registrar equipo' }));

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Ingresá el ID de la competencia.');
    expect(alert).toHaveTextContent('Integrante 1: ingresá el nombre completo.');
    expect(alert).toHaveTextContent('Robot: el peso debe ser un número.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('reports a document without reference and an empty weight', async () => {
    const fetchMock = mockFetch(jsonResponse({}, 201));
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    await fillValidForm(user);
    await user.clear(screen.getByLabelText('Peso (kg)'));
    await user.click(screen.getByRole('button', { name: 'Agregar documento' }));
    await user.click(screen.getByRole('button', { name: 'Registrar equipo' }));

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Robot: el peso debe ser un número.');
    expect(alert).toHaveTextContent('Documento 1: ingresá la referencia.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('lets the user add and remove members and documents', async () => {
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    expect(screen.queryByRole('button', { name: 'Quitar integrante 1' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Agregar integrante' }));
    expect(screen.getByRole('group', { name: 'Integrante 2' })).toBeInTheDocument();
    await user.selectOptions(
      within(screen.getByRole('group', { name: 'Integrante 2' })).getByLabelText('Rol'), 'COACH');
    await user.click(screen.getByRole('button', { name: 'Quitar integrante 1' }));
    expect(screen.queryByRole('group', { name: 'Integrante 2' })).not.toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Integrante 1' })).getByLabelText('Rol')).toHaveValue('COACH');

    await user.click(screen.getByRole('button', { name: 'Agregar documento' }));
    await user.selectOptions(
      within(screen.getByRole('group', { name: 'Documento 1' })).getByLabelText('Tipo de documento'), 'INSURANCE');
    expect(within(screen.getByRole('group', { name: 'Documento 1' })).getByLabelText('Tipo de documento'))
      .toHaveValue('INSURANCE');
    await user.click(screen.getByRole('button', { name: 'Quitar documento 1' }));
    expect(screen.queryByRole('group', { name: 'Documento 1' })).not.toBeInTheDocument();
  });

  it('disables the submit button while the request is in flight', async () => {
    let resolve: (response: Response) => void = () => {};
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>((done) => { resolve = done; })));
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: 'Registrar equipo' }));
    expect(screen.getByRole('button', { name: 'Enviando…' })).toBeDisabled();

    resolve(jsonResponse({ teamId: 'team-3', status: 'ACCEPTED', violations: [] }, 201));
    expect(await screen.findByRole('status')).toHaveTextContent('team-3');
    expect(screen.getByRole('button', { name: 'Registrar equipo' })).toBeEnabled();
  });

  it('consults a registration by id and shows its status, violations and data', async () => {
    const fetchMock = mockFetch(jsonResponse({
      teamId: 'TEAM-7', competitionId: 'COMPETITION-1', categoryId: 'CATEGORY-1', name: 'Rescue Crew',
      status: 'REJECTED',
      members: [{ memberId: 'MEMBER-1', fullName: 'Ada', birthDate: '2010-05-20', role: 'COMPETITOR' }],
      robot: { name: 'Rex', robotClass: 'RESCUE_BOT', weightKg: 2.4, dimensions: { lengthMm: 200, widthMm: 150, heightMm: 120 } },
      documentTypes: ['TECHNICAL_SHEET'],
      violations: [{ code: 'REQUIRED_DOCUMENTS', reason: 'missing PARENTAL_CONSENT' }],
    }, 200));
    const user = userEvent.setup();
    render(<RegisterTeamForm />);

    await user.click(screen.getByRole('button', { name: 'Consultar equipo' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('El ID del equipo es obligatorio.');
    expect(fetchMock).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText('ID del equipo a consultar'), 'TEAM-7');
    await user.click(screen.getByRole('button', { name: 'Consultar equipo' }));

    expect(await screen.findByText('Inscripción rechazada')).toBeInTheDocument();
    expect(screen.getByText(/missing PARENTAL_CONSENT/)).toBeInTheDocument();
    expect(screen.getByText('Rex (RESCUE_BOT, 2.4 kg, 200×150×120 mm)')).toBeInTheDocument();
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe('/teams/TEAM-7');
  });
});
