import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AuditPage from './AuditPage';
import type { AuditEvent, AuditTrail } from './types';

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

const CAPTURED: AuditEvent = {
  occurredAt: '2026-03-01T10:00:00.123456Z',
  action: 'RESULT_CAPTURED',
  subjectId: 'RUN-1',
  actor: 'dev-seed',
  details: { measurements: '{ENERGY=48, OBJECTIVES=5, TIME=99}', rulebook: 'v1' },
};
const CORRECTED: AuditEvent = {
  occurredAt: '2026-03-01T11:30:05Z',
  action: 'RESULT_CORRECTED',
  subjectId: 'RUN-1',
  actor: 'reviewer',
  details: {
    corrected: '{ENERGY=48, OBJECTIVES=5, TIME=120}',
    original: '{ENERGY=48, OBJECTIVES=5, TIME=99}',
    reason: 'appeal APPEAL-1 accepted',
  },
};

function trail(subjectId: string, events: AuditEvent[]): AuditTrail {
  return { subjectId, events };
}

async function consult(user: ReturnType<typeof userEvent.setup>, subjectId = 'RUN-1') {
  await user.type(screen.getByLabelText('ID del sujeto'), subjectId);
  await user.click(screen.getByRole('button', { name: 'Consultar auditoría' }));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('AuditPage', () => {
  it('asks the API for the subject and lists its events in the order received', async () => {
    const fetchMock = mockFetch(jsonResponse(trail('RUN-1', [CAPTURED, CORRECTED]), 200));
    const user = userEvent.setup();
    render(<AuditPage />);

    await consult(user, '  RUN-1 ');

    expect(await screen.findByRole('heading', { name: 'Auditoría de RUN-1' })).toBeInTheDocument();
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe('/audit?subjectId=RUN-1');
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByRole('heading')).toHaveTextContent('Resultado capturado (RESULT_CAPTURED)');
    expect(within(items[1]).getByRole('heading')).toHaveTextContent('Resultado corregido (RESULT_CORRECTED)');
  });

  it('shows the instant, the actor and the registered details of an event', async () => {
    mockFetch(jsonResponse(trail('RUN-1', [CAPTURED]), 200));
    const user = userEvent.setup();
    render(<AuditPage />);

    await consult(user);

    const item = await screen.findByRole('listitem');
    const time = within(item).getByText('2026-03-01 10:00:00 UTC');
    expect(time).toHaveAttribute('datetime', '2026-03-01T10:00:00.123456Z');
    expect(item).toHaveTextContent('por dev-seed');
    expect(within(item).getByText('Mediciones').nextElementSibling).toHaveTextContent(
      '{ENERGY=48, OBJECTIVES=5, TIME=99}',
    );
    expect(within(item).getByText('Reglamento').nextElementSibling).toHaveTextContent('v1');
  });

  it('tells the original values from the corrected ones and shows the reason', async () => {
    mockFetch(jsonResponse(trail('RUN-1', [CAPTURED, CORRECTED]), 200));
    const user = userEvent.setup();
    render(<AuditPage />);

    await consult(user);

    const correction = (await screen.findAllByRole('listitem'))[1];
    expect(within(correction).getByText('Valores originales').nextElementSibling).toHaveTextContent('TIME=99');
    expect(within(correction).getByText('Valores corregidos').nextElementSibling).toHaveTextContent('TIME=120');
    expect(within(correction).getByText('Motivo').nextElementSibling).toHaveTextContent('appeal APPEAL-1 accepted');
  });

  it('shows the submission and the resolution of an appeal with its status and rationale', async () => {
    const submitted: AuditEvent = {
      occurredAt: '2026-03-01T11:00:00Z',
      action: 'APPEAL_SUBMITTED',
      subjectId: 'APPEAL-1',
      actor: 'team',
      details: { run: 'RUN-1' },
    };
    const resolved: AuditEvent = {
      occurredAt: '2026-03-01T11:30:05Z',
      action: 'APPEAL_RESOLVED',
      subjectId: 'APPEAL-1',
      actor: 'reviewer',
      details: { status: 'REJECTED', rationale: 'the recording shows a correct start' },
    };
    mockFetch(jsonResponse(trail('APPEAL-1', [submitted, resolved]), 200));
    const user = userEvent.setup();
    render(<AuditPage />);

    await consult(user, 'APPEAL-1');

    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Apelación presentada');
    expect(within(items[0]).getByText('Corrida').nextElementSibling).toHaveTextContent('RUN-1');
    expect(items[1]).toHaveTextContent('Apelación resuelta');
    expect(within(items[1]).getByText('Estado').nextElementSibling).toHaveTextContent('REJECTED');
    expect(within(items[1]).getByText('Fundamento').nextElementSibling).toHaveTextContent(
      'the recording shows a correct start',
    );
  });

  it('shows an event without details and falls back to the codes it does not know', async () => {
    const unknown: AuditEvent = {
      occurredAt: '2026-03-01T09:00:00Z',
      action: 'SOMETHING_NEW',
      subjectId: 'RUN-1',
      actor: 'x',
      details: { newKey: 'value' },
    };
    const plain: AuditEvent = { ...CAPTURED, action: 'ROUND_SCHEDULED', details: {} };
    mockFetch(jsonResponse(trail('RUN-1', [unknown, plain]), 200));
    const user = userEvent.setup();
    render(<AuditPage />);

    await consult(user);

    const items = await screen.findAllByRole('listitem');
    expect(items[0]).toHaveTextContent('SOMETHING_NEW');
    expect(within(items[0]).getByText('newKey').nextElementSibling).toHaveTextContent('value');
    expect(items[1]).toHaveTextContent('Ronda programada');
    expect(items[1].querySelector('dl')).toBeNull();
  });

  it('says there are no events instead of an error when the trail is empty', async () => {
    mockFetch(jsonResponse(trail('RUN-99', []), 200));
    const user = userEvent.setup();
    render(<AuditPage />);

    await consult(user, 'RUN-99');

    expect(await screen.findByRole('status')).toHaveTextContent('No hay eventos registrados para RUN-99.');
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('requires an identifier before calling the API', async () => {
    const fetchMock = mockFetch();
    const user = userEvent.setup();
    render(<AuditPage />);

    await user.type(screen.getByLabelText('ID del sujeto'), '   ');
    await user.click(screen.getByRole('button', { name: 'Consultar auditoría' }));

    expect(screen.getByRole('alert')).toHaveTextContent('El ID del sujeto es obligatorio.');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows the API error for an identifier it does not support and drops the previous trail', async () => {
    mockFetch(
      jsonResponse(trail('RUN-1', [CAPTURED]), 200),
      jsonResponse({ code: 'INVALID_VALUE', message: "subjectId 'FOO-1' is not a supported identifier" }, 400),
    );
    const user = userEvent.setup();
    render(<AuditPage />);

    await consult(user);
    await screen.findByRole('heading', { name: 'Auditoría de RUN-1' });
    await user.clear(screen.getByLabelText('ID del sujeto'));
    await consult(user, 'FOO-1');

    expect(await screen.findByRole('alert')).toHaveTextContent('is not a supported identifier');
    expect(screen.getByText('Código: INVALID_VALUE')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Auditoría de RUN-1' })).not.toBeInTheDocument();
  });

  it('encodes the identifier in the query and reports a network failure', async () => {
    const fetchMock = mockFetch(new Error('offline'));
    const user = userEvent.setup();
    render(<AuditPage />);

    await consult(user, 'RUN 1&x');

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor.');
    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe('/audit?subjectId=RUN%201%26x');
  });
});
