import { useState, type FormEvent } from 'react';
import type { ApiRequestError } from '../../api/http';
import { createSeason } from './api';
import Field from './Field';
import { emptySeason, seasonProblems, seasonRequest, type SeasonDraft } from './formModel';
import { ApiProblem, ProblemList, asApiError } from './Problems';

export default function SeasonForm({ actor, onCreated }: { actor: string; onCreated: (seasonId: string) => void }) {
  const [draft, setDraft] = useState<SeasonDraft>(emptySeason);
  const [problems, setProblems] = useState<string[]>([]);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [created, setCreated] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setCreated(null);
    const found = seasonProblems(draft, actor);
    setProblems(found);
    if (found.length > 0) {
      return;
    }
    setBusy(true);
    try {
      const { seasonId } = await createSeason(seasonRequest(draft, actor));
      setCreated(seasonId);
      onCreated(seasonId);
    } catch (failure) {
      setError(asApiError(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-label="Temporada">
      <form onSubmit={submit} noValidate>
        <h3>1. Temporada</h3>
        <fieldset>
          <legend>Temporada</legend>
          <Field label="Nombre de la temporada" value={draft.name} onChange={(name) => setDraft({ ...draft, name })} />
          <Field label="Año" type="number" value={draft.year} onChange={(year) => setDraft({ ...draft, year })} />
          <Field label="Inicio de la temporada" type="date" value={draft.start}
            onChange={(start) => setDraft({ ...draft, start })} />
          <Field label="Fin de la temporada" type="date" value={draft.end}
            onChange={(end) => setDraft({ ...draft, end })} />
        </fieldset>
        <ProblemList problems={problems} />
        <button type="submit" disabled={busy}>{busy ? 'Creando…' : 'Crear temporada'}</button>
      </form>
      {error && <ApiProblem title="No se pudo crear la temporada" error={error} />}
      {created && (
        <p className="notice" role="status">
          Temporada creada: <strong>{created}</strong>
        </p>
      )}
    </section>
  );
}
