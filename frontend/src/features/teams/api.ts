import type { RegisterTeamRequest, RegistrationOutcome } from './types';

/** Error devuelto por la API (`{code, message}`) o fallo de red / respuesta inesperada. */
export class ApiRequestError extends Error {
  readonly code: string;
  readonly httpStatus: number;

  constructor(code: string, message: string, httpStatus: number) {
    super(message);
    this.name = 'ApiRequestError';
    this.code = code;
    this.httpStatus = httpStatus;
  }
}

export async function registerTeam(request: RegisterTeamRequest): Promise<RegistrationOutcome> {
  let response: Response;
  try {
    response = await fetch('/teams', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });
  } catch {
    throw new ApiRequestError('NETWORK_ERROR', 'No se pudo conectar con el servidor.', 0);
  }
  if (!response.ok) {
    throw await errorOf(response);
  }
  return (await response.json()) as RegistrationOutcome;
}

async function errorOf(response: Response): Promise<ApiRequestError> {
  try {
    const body: unknown = await response.json();
    if (isApiError(body)) {
      return new ApiRequestError(body.code, body.message, response.status);
    }
  } catch {
    // El cuerpo no era JSON: se informa como respuesta inesperada.
  }
  return new ApiRequestError(
    'UNEXPECTED_ERROR',
    `Respuesta inesperada del servidor (HTTP ${response.status}).`,
    response.status,
  );
}

function isApiError(body: unknown): body is { code: string; message: string } {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as { code?: unknown }).code === 'string' &&
    typeof (body as { message?: unknown }).message === 'string'
  );
}
