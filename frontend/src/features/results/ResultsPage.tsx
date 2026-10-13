import { useState, type FormEvent } from 'react';
import { ApiRequestError } from '../../api/http';
import { captureAutomatic, captureComplete, capturePanel, findRunResult } from './api';
import { emptyCaptureForm, parseCapture, type CaptureFormState } from './formModel';
import RunResultView from './RunResultView';
import type { AutomaticCaptureRequest, CompleteCaptureRequest, PanelCaptureRequest, RunResult } from './types';

export default function ResultsPage() {
  const [form, setForm] = useState<CaptureFormState>(emptyCaptureForm);
  const [runId, setRunId] = useState('');
  const [problems, setProblems] = useState<string[]>([]);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [result, setResult] = useState<RunResult | null>(null);
  const [busy, setBusy] = useState(false);

  const update = (change: Partial<CaptureFormState>) => setForm((current) => ({ ...current, ...change }));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsed = parseCapture(form);
    setProblems(parsed.problems);
    setError(null);
    if (!parsed.request) return;
    setBusy(true);
    try {
      const captured = form.mode === 'complete'
        ? await captureComplete(parsed.request as CompleteCaptureRequest)
        : form.mode === 'automatic'
          ? await captureAutomatic(parsed.request as AutomaticCaptureRequest)
          : await capturePanel(parsed.request as PanelCaptureRequest);
      setResult(captured);
      setRunId(captured.runId);
    } catch (failure) {
      setError(asApiError(failure));
    } finally {
      setBusy(false);
    }
  }

  async function consult() {
    setError(null);
    if (runId.trim() === '') {
      setProblems(['El ID de la corrida es obligatorio para consultar.']);
      return;
    }
    setProblems([]);
    setBusy(true);
    try {
      setResult(await findRunResult(runId.trim()));
    } catch (failure) {
      setError(asApiError(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <form onSubmit={submit} noValidate>
        <h2>Capturar resultados</h2>
        <fieldset>
          <legend>Modalidad</legend>
          <label>Tipo de captura
            <select value={form.mode} onChange={(event) => update({ mode: event.target.value as CaptureFormState['mode'] })}>
              <option value="complete">Completa</option>
              <option value="automatic">Fuente automática</option>
              <option value="panel">Fuente de jueces</option>
            </select>
          </label>
        </fieldset>
        <fieldset>
          <legend>Intento</legend>
          <Field label="ID de la ronda" value={form.roundId} onChange={(roundId) => update({ roundId })} />
          <Field label="ID del equipo" value={form.teamId} onChange={(teamId) => update({ teamId })} />
          <Field label="Número de intento" type="number" value={form.attemptNumber}
            onChange={(attemptNumber) => update({ attemptNumber })} />
          <Field label="Registrado por" value={form.actor} onChange={(actor) => update({ actor })} />
        </fieldset>
        {form.mode !== 'panel' && <JsonField label="Mediciones" value={form.measurements}
          onChange={(measurements) => update({ measurements })} />}
        {form.mode !== 'automatic' && <JsonField label="Evaluaciones" value={form.evaluations}
          onChange={(evaluations) => update({ evaluations })} />}
        {form.mode !== 'automatic' && <JsonField label="Incidentes" value={form.incidents}
          onChange={(incidents) => update({ incidents })} />}
        <Problems problems={problems} />
        <button type="submit" disabled={busy}>{busy ? 'Enviando…' : 'Registrar resultado'}</button>
      </form>

      <section className="lookup" aria-labelledby="run-lookup-title">
        <h2 id="run-lookup-title">Consultar corrida</h2>
        <Field label="ID de la corrida" value={runId} onChange={setRunId} />
        <button type="button" disabled={busy} onClick={consult}>Consultar</button>
      </section>

      {error && <section className="result result--error" role="alert">
        <h2>No se pudo registrar o consultar la corrida</h2>
        <p>{error.message}</p><p className="code">Código: {error.code}</p>
      </section>}
      {result && <RunResultView result={result} />}
    </>
  );
}

function Field(props: { label: string; value: string; onChange: (value: string) => void; type?: string }) {
  return <label>{props.label}<input type={props.type ?? 'text'} value={props.value}
    onChange={(event) => props.onChange(event.target.value)} /></label>;
}

function JsonField(props: { label: string; value: string; onChange: (value: string) => void }) {
  return <label>{props.label}<textarea className="json-editor" rows={5} value={props.value}
    onChange={(event) => props.onChange(event.target.value)} /></label>;
}

function Problems({ problems }: { problems: string[] }) {
  return problems.length === 0 ? null : <div className="problems" role="alert"><p>Revisá los datos:</p><ul>
    {problems.map((problem) => <li key={problem}>{problem}</li>)}
  </ul></div>;
}

function asApiError(failure: unknown): ApiRequestError {
  return failure instanceof ApiRequestError ? failure
    : new ApiRequestError('UNEXPECTED_ERROR', 'Ocurrió un error inesperado.', 0);
}
