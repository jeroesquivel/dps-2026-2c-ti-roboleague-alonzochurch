import { useState } from 'react';
import { ApiRequestError } from '../../api/http';
import { findRunScore } from './api';
import ScoreView from './ScoreView';
import type { RunScore } from './types';

export default function ScorePage() {
  const [runId, setRunId] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [score, setScore] = useState<RunScore | null>(null);
  const [busy, setBusy] = useState(false);

  async function consult() {
    setError(null);
    const id = runId.trim();
    if (id === '') {
      setProblem('El ID de la corrida es obligatorio.');
      return;
    }
    setProblem(null);
    setBusy(true);
    try {
      setScore(await findRunScore(id));
    } catch (failure) {
      setScore(null);
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
        <h2>Puntaje de una corrida</h2>
        <fieldset>
          <legend>Corrida</legend>
          <label>
            ID de la corrida
            <input type="text" value={runId} onChange={(event) => setRunId(event.target.value)} />
          </label>
        </fieldset>
        {problem && (
          <div className="problems" role="alert">
            <p>{problem}</p>
          </div>
        )}
        <div className="actions">
          <button type="button" disabled={busy} onClick={consult}>Consultar puntaje</button>
        </div>
      </form>

      {error && (
        <section className="result result--error" role="alert">
          <h2>No se pudo completar la operación</h2>
          <p>{error.message}</p>
          <p className="code">Código: {error.code}</p>
        </section>
      )}
      {score && <ScoreView score={score} />}
    </>
  );
}
