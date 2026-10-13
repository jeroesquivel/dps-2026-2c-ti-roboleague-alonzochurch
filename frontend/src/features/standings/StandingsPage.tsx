import { useState } from 'react';
import { ApiRequestError } from '../../api/http';
import { findLatestStandings, findRevisions, generateStandings, publishStandings, recalculateStandings } from './api';
import RevisionList from './RevisionList';
import StandingsView from './StandingsView';
import type { Standings } from './types';

interface FormState {
  competitionId: string;
  categoryId: string;
  actor: string;
  reason: string;
}

function problemsOf(form: FormState, needsActor: boolean, needsReason = false): string[] {
  const problems: string[] = [];
  if (form.competitionId.trim() === '') {
    problems.push('El ID de la competencia es obligatorio.');
  }
  if (form.categoryId.trim() === '') {
    problems.push('El ID de la categoría es obligatorio.');
  }
  if (needsActor && form.actor.trim() === '') {
    problems.push('Indicá quién realiza la operación.');
  }
  if (needsReason && form.reason.trim() === '') {
    problems.push('Indicá el motivo del recálculo.');
  }
  return problems;
}

export default function StandingsPage() {
  const [form, setForm] = useState<FormState>({ competitionId: '', categoryId: '', actor: '', reason: '' });
  const [problems, setProblems] = useState<string[]>([]);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [latest, setLatest] = useState<Standings | null>(null);
  const [revisions, setRevisions] = useState<Standings[] | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);

  const competitionId = form.competitionId.trim();
  const categoryId = form.categoryId.trim();
  const actor = form.actor.trim();
  const shown = (revisions && selected !== null && revisions.find((r) => r.revision === selected)) || latest;
  const showingLatest = shown !== null && latest !== null && shown.revision === latest.revision;

  /** Valida el formulario, ejecuta la operación y traduce sus fallos a un mensaje para la pantalla. */
  async function perform(needsActor: boolean, operation: () => Promise<void>, needsReason = false) {
    setError(null);
    setNotice(null);
    const found = problemsOf(form, needsActor, needsReason);
    setProblems(found);
    if (found.length > 0) {
      return;
    }
    setBusy(true);
    try {
      await operation();
    } catch (failure) {
      setError(
        failure instanceof ApiRequestError
          ? failure
          : new ApiRequestError('UNEXPECTED_ERROR', 'Ocurrió un error inesperado.', 0),
      );
    } finally {
      setBusy(false);
    }
  }

  function show(standings: Standings) {
    setLatest(standings);
    setRevisions(null);
    setSelected(null);
  }

  const consult = () => perform(false, async () => show(await findLatestStandings(competitionId, categoryId)));

  const generate = () =>
    perform(true, async () => {
      const generated = await generateStandings(competitionId, categoryId, actor);
      show(generated);
      setNotice(`Se generó la revisión ${generated.revision} de las posiciones.`);
    });

  const publish = () =>
    perform(true, async () => {
      const published = await publishStandings(competitionId, categoryId, actor);
      show(published);
      setNotice(`La revisión ${published.revision} quedó publicada como final.`);
    });

  const recalculate = () =>
    perform(
      true,
      async () => {
        const recalculated = await recalculateStandings(competitionId, categoryId, form.reason.trim(), actor);
        show(recalculated);
        setNotice(`Se recalcularon las posiciones: nueva revisión ${recalculated.revision}.`);
      },
      true,
    );

  const loadHistory = () =>
    perform(false, async () => {
      const history = await findRevisions(competitionId, categoryId);
      setRevisions(history.revisions);
      setSelected(history.revisions.at(-1)?.revision ?? null);
    });

  return (
    <>
      <form onSubmit={(event) => event.preventDefault()} noValidate>
        <h2>Posiciones</h2>
        <fieldset>
          <legend>Categoría</legend>
          <Field label="ID de la competencia" value={form.competitionId}
            onChange={(value) => setForm({ ...form, competitionId: value })} />
          <Field label="ID de la categoría" value={form.categoryId}
            onChange={(value) => setForm({ ...form, categoryId: value })} />
          <Field label="Operación realizada por" value={form.actor}
            onChange={(value) => setForm({ ...form, actor: value })} />
          <Field label="Motivo del recálculo" value={form.reason}
            onChange={(value) => setForm({ ...form, reason: value })} />
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
          <button type="button" disabled={busy} onClick={consult}>Consultar posiciones</button>
          <button type="button" disabled={busy} onClick={generate}>Generar posiciones</button>
          <button type="button" disabled={busy} onClick={recalculate}>Recalcular posiciones</button>
          <button type="button" disabled={busy} onClick={loadHistory}>Ver historial</button>
        </div>
      </form>

      {error && (
        <section className="result result--error" role="alert">
          <h2>No se pudo completar la operación</h2>
          <p>{error.message}</p>
          <p className="code">Código: {error.code}</p>
        </section>
      )}
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}

      {revisions && selected !== null && (
        <RevisionList revisions={revisions} selected={selected} onSelect={setSelected} />
      )}
      {shown && <StandingsView standings={shown} />}
      {shown && showingLatest && shown.status === 'PROVISIONAL' && (
        <button type="button" className="publish" disabled={busy} onClick={publish}>
          Publicar como final
        </button>
      )}
    </>
  );
}

function Field(props: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label>
      {props.label}
      <input type="text" value={props.value} onChange={(event) => props.onChange(event.target.value)} />
    </label>
  );
}
