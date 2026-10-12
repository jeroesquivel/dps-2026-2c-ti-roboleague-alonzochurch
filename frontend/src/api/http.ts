/** Un conflicto de agenda informado por la API (`409 SCHEDULE_CONFLICT`). */
export interface ScheduleConflict {
  type: string;
  detail: string;
}

/** Error devuelto por la API (`{code, message}`) o fallo de red / respuesta inesperada. */
export class ApiRequestError extends Error {
  readonly code: string;
  readonly httpStatus: number;
  readonly conflicts: ScheduleConflict[];

  constructor(code: string, message: string, httpStatus: number, conflicts: ScheduleConflict[] = []) {
    super(message);
    this.name = 'ApiRequestError';
    this.code = code;
    this.httpStatus = httpStatus;
    this.conflicts = conflicts;
  }
}

/** Envía una petición a la API y devuelve la respuesta si fue exitosa; si no, lanza `ApiRequestError`. */
export async function request(url: string, init?: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(url, init);
  } catch {
    throw new ApiRequestError('NETWORK_ERROR', 'No se pudo conectar con el servidor.', 0);
  }
  if (!response.ok) {
    throw await errorOf(response);
  }
  return response;
}

export async function postJson<T>(url: string, body: unknown): Promise<T> {
  const response = await request(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return (await response.json()) as T;
}

export async function getJson<T>(url: string): Promise<T> {
  return (await (await request(url)).json()) as T;
}

async function errorOf(response: Response): Promise<ApiRequestError> {
  try {
    const body: unknown = await response.json();
    if (isApiError(body)) {
      return new ApiRequestError(body.code, body.message, response.status, conflictsOf(body));
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

function isApiError(body: unknown): body is { code: string; message: string; conflicts?: unknown } {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as { code?: unknown }).code === 'string' &&
    typeof (body as { message?: unknown }).message === 'string'
  );
}

function conflictsOf(body: { conflicts?: unknown }): ScheduleConflict[] {
  if (!Array.isArray(body.conflicts)) {
    return [];
  }
  return body.conflicts.filter(
    (item): item is ScheduleConflict =>
      typeof item === 'object' &&
      item !== null &&
      typeof (item as { type?: unknown }).type === 'string' &&
      typeof (item as { detail?: unknown }).detail === 'string',
  );
}
