import { useState } from 'react';
import { ApiRequestError } from '../../api/http';
import { findAuditTrail } from './api';
import AuditTrailView from './AuditTrailView';
import { SUBJECT_EXAMPLES } from './labels';
import type { AuditTrail } from './types';

export default function AuditPage() {
  const [subjectId, setSubjectId] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [trail, setTrail] = useState<AuditTrail | null>(null);
  const [busy, setBusy] = useState(false);

  async function consult() {
    setError(null);
    const id = subjectId.trim();
    if (id === '') {
      setProblem('El ID del sujeto es obligatorio.');
      return;
    }
    setProblem(null);
    setBusy(true);
    try {
      setTrail(await findAuditTrail(id));
    } catch (failure) {
      setTrail(null);
      setError(
        failure instanceof ApiRequestError
          ? failure
          : new ApiRequestError('UNEXPECTED_ERROR', 'Ocurrió un error inesperado.', 0),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <form onSubmit={(event) => event.preventDefault()} noValidate>
        <h2>Auditoría de una operación</h2>
        <fieldset>
          <legend>Sujeto</legend>
          <label>
            ID del sujeto
            <input type="text" value={subjectId} placeholder="RUN-1"
              onChange={(event) => setSubjectId(event.target.value)} />
          </label>
          <span className="meta">Por ejemplo: {SUBJECT_EXAMPLES}.</span>
        </fieldset>
        {problem && (
          <div className="problems" role="alert">
            <p>{problem}</p>
          </div>
        )}
        <div className="actions">
          <button type="button" disabled={busy} onClick={consult}>Consultar auditoría</button>
        </div>
      </form>

      {error && (
        <section className="result result--error" role="alert">
          <h2>No se pudo completar la operación</h2>
          <p>{error.message}</p>
          <p className="code">Código: {error.code}</p>
        </section>
      )}
      {trail && <AuditTrailView trail={trail} />}
    </>
  );
}
