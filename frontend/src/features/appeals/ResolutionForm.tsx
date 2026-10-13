import { useState } from 'react';
import {
  acceptanceProblems,
  draftOf,
  rejectionProblems,
  toAcceptRequest,
  toRejectRequest,
  type IncidentRow,
  type MeasurementRow,
} from './formModel';
import type { AcceptAppealRequest, RejectAppealRequest, Run } from './types';

interface Props {
  run: Run | null;
  busy: boolean;
  onAccept: (request: AcceptAppealRequest) => void;
  onReject: (request: RejectAppealRequest) => void;
}

/**
 * Resolución de una apelación pendiente. Aceptar y rechazar son dos acciones distintas; la
 * corrección se prepara a partir del resultado vigente que informa la API, y es el conjunto
 * completo de mediciones e incidentes que quedará en la corrida.
 */
export default function ResolutionForm({ run, busy, onAccept, onReject }: Props) {
  const [draft, setDraft] = useState(() => draftOf(run));
  const [problems, setProblems] = useState<string[]>([]);

  const setMeasurement = (index: number, value: string) =>
    setDraft({ ...draft, measurements: replaceAt<MeasurementRow>(draft.measurements, index, { value }) });
  const setIncident = (index: number, change: Partial<IncidentRow>) =>
    setDraft({ ...draft, incidents: replaceAt<IncidentRow>(draft.incidents, index, change) });

  function accept() {
    const found = acceptanceProblems(draft);
    setProblems(found);
    if (found.length === 0) {
      onAccept(toAcceptRequest(draft));
    }
  }

  function reject() {
    const found = rejectionProblems(draft);
    setProblems(found);
    if (found.length === 0) {
      onReject(toRejectRequest(draft));
    }
  }

  return (
    <form onSubmit={(event) => event.preventDefault()} noValidate aria-label="Resolver apelación">
      <h2>Resolver apelación</h2>
      <fieldset>
        <legend>Decisión</legend>
        <label>
          Revisor
          <input type="text" value={draft.reviewer} onChange={(e) => setDraft({ ...draft, reviewer: e.target.value })} />
        </label>
        <label>
          Fundamento
          <input type="text" value={draft.rationale}
            onChange={(e) => setDraft({ ...draft, rationale: e.target.value })} />
        </label>
      </fieldset>

      <fieldset>
        <legend>Corrección (sólo si se acepta)</legend>
        {run === null ? (
          <p>No se pudo cargar el resultado vigente de la corrida, por lo que no se puede preparar una corrección.</p>
        ) : (
          <>
            <p className="meta">
              Se envía el conjunto completo de mediciones e incidentes; parte del resultado vigente.
            </p>
            {draft.measurements.map((row, index) => (
              <label key={row.key}>
                {`Medición ${row.key}`}
                <input type="text" inputMode="decimal" value={row.value}
                  onChange={(e) => setMeasurement(index, e.target.value)} />
              </label>
            ))}
            {draft.incidents.map((row, index) => (
              <div className="incident-row" key={index}>
                <label>
                  {`Código del incidente ${index + 1}`}
                  <input type="text" value={row.code} onChange={(e) => setIncident(index, { code: e.target.value })} />
                </label>
                <label>
                  {`Ocurrencias del incidente ${index + 1}`}
                  <input type="text" inputMode="numeric" value={row.occurrences}
                    onChange={(e) => setIncident(index, { occurrences: e.target.value })} />
                </label>
                <button type="button" onClick={() => setDraft({ ...draft, incidents: without(draft.incidents, index) })}>
                  {`Quitar incidente ${index + 1}`}
                </button>
              </div>
            ))}
            <button type="button"
              onClick={() => setDraft({ ...draft, incidents: [...draft.incidents, { code: '', occurrences: '1' }] })}>
              Agregar incidente
            </button>
          </>
        )}
      </fieldset>

      {problems.length > 0 && (
        <div className="problems" role="alert">
          <p>Revisá los datos antes de continuar:</p>
          <ul>
            {problems.map((problem) => (
              <li key={problem}>{problem}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="actions">
        <button type="button" disabled={busy || run === null} onClick={accept}>Aceptar apelación</button>
        <button type="button" disabled={busy} onClick={reject}>Rechazar apelación</button>
      </div>
    </form>
  );
}

function replaceAt<T>(items: T[], index: number, change: Partial<T>): T[] {
  return items.map((item, position) => (position === index ? { ...item, ...change } : item));
}

function without<T>(items: T[], index: number): T[] {
  return items.filter((_, position) => position !== index);
}
