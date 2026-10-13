import { useEffect, useState, type FormEvent } from 'react';
import type { ApiRequestError } from '../../api/http';
import { createCompetition } from './api';
import Field from './Field';
import {
  competitionProblems,
  competitionRequest,
  emptyCategory,
  emptyCompetition,
  type CategoryDraft,
  type CompetitionDraft,
} from './formModel';
import { ApiProblem, ProblemList, asApiError } from './Problems';
import RulebookEditor from './RulebookEditor';
import type { CompetitionCreated } from './types';

interface Props {
  actor: string;
  /** Temporada obtenida en el paso anterior; se carga sola pero se puede cambiar. */
  seasonId: string;
  onCreated: (created: CompetitionCreated) => void;
}

export default function CompetitionForm({ actor, seasonId, onCreated }: Props) {
  const [draft, setDraft] = useState<CompetitionDraft>(() => emptyCompetition(seasonId));
  const [problems, setProblems] = useState<string[]>([]);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [created, setCreated] = useState<CompetitionCreated | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setDraft((current) => ({ ...current, seasonId }));
  }, [seasonId]);

  const updateCategory = (index: number, change: Partial<CategoryDraft>) =>
    setDraft((current) => ({
      ...current,
      categories: current.categories.map((category, i) => (i === index ? { ...category, ...change } : category)),
    }));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setCreated(null);
    const found = competitionProblems(draft, actor);
    setProblems(found);
    if (found.length > 0) {
      return;
    }
    setBusy(true);
    try {
      const result = await createCompetition(draft.seasonId.trim(), competitionRequest(draft, actor));
      setCreated(result);
      onCreated(result);
    } catch (failure) {
      setError(asApiError(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-label="Competencia">
      <form onSubmit={submit} noValidate>
        <h3>2. Competencia y reglamento inicial</h3>
        <fieldset>
          <legend>Competencia</legend>
          <Field label="ID de la temporada" value={draft.seasonId}
            onChange={(value) => setDraft({ ...draft, seasonId: value })} />
          <Field label="Nombre de la competencia" value={draft.name}
            onChange={(name) => setDraft({ ...draft, name })} />
          <Field label="Inicio de la competencia" type="date" value={draft.start}
            onChange={(start) => setDraft({ ...draft, start })} />
          <Field label="Fin de la competencia" type="date" value={draft.end}
            onChange={(end) => setDraft({ ...draft, end })} />
        </fieldset>

        {draft.categories.map((category, index) => (
          <fieldset key={index} aria-label={`Categoría ${index + 1}`}>
            <legend>{`Categoría ${index + 1}`}</legend>
            <Field label="Nombre de la categoría" value={category.name}
              onChange={(name) => updateCategory(index, { name })} />
            <Field label="Edad mínima" type="number" value={category.minimumAge}
              onChange={(minimumAge) => updateCategory(index, { minimumAge })} />
            <Field label="Edad máxima" type="number" value={category.maximumAge}
              onChange={(maximumAge) => updateCategory(index, { maximumAge })} />
            <Field label="Clase de robot" value={category.robotClass}
              onChange={(robotClass) => updateCategory(index, { robotClass })} />
            {draft.categories.length > 1 && (
              <button type="button"
                onClick={() => setDraft({ ...draft, categories: draft.categories.filter((_, i) => i !== index) })}>
                {`Quitar categoría ${index + 1}`}
              </button>
            )}
          </fieldset>
        ))}
        <button type="button" onClick={() => setDraft({ ...draft, categories: [...draft.categories, emptyCategory()] })}>
          Agregar categoría
        </button>

        <RulebookEditor title="Reglamento inicial" value={draft.rulebook}
          onChange={(rulebook) => setDraft({ ...draft, rulebook })} />
        <ProblemList problems={problems} />
        <button type="submit" disabled={busy}>{busy ? 'Creando…' : 'Crear competencia'}</button>
      </form>
      {error && <ApiProblem title="No se pudo crear la competencia" error={error} />}
      {created && (
        <div className="notice" role="status">
          <p>
            Competencia creada: <strong>{created.competitionId}</strong> (reglamento {created.rulebookVersion})
          </p>
          <table>
            <caption>Categorías creadas</caption>
            <thead>
              <tr><th>Categoría</th><th>ID</th></tr>
            </thead>
            <tbody>
              {created.categories.map((category) => (
                <tr key={category.categoryId}><td>{category.name}</td><td>{category.categoryId}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
