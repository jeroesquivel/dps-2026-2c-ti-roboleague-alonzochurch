import { useState, type FormEvent } from 'react';
import { ApiRequestError } from '../../api/http';
import { findRound, scheduleRound } from './api';
import { emptyForm, emptyHeat, problemsOf, toRequest, type FormState, type HeatDraft } from './formModel';
import RoundView from './RoundView';
import ScheduleConflicts from './ScheduleConflicts';
import type { Round } from './types';

interface Scheduled {
  roundId: string;
  /** `null` cuando la ronda se creó pero no se pudo cargar su detalle. */
  round: Round | null;
}

export default function ScheduleRoundForm() {
  const [form, setForm] = useState<FormState>(emptyForm);
  const [problems, setProblems] = useState<string[]>([]);
  const [apiError, setApiError] = useState<ApiRequestError | null>(null);
  const [scheduled, setScheduled] = useState<Scheduled | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const updateHeat = (index: number, change: Partial<HeatDraft>) =>
    setForm((current) => ({
      ...current,
      heats: current.heats.map((heat, i) => (i === index ? { ...heat, ...change } : heat)),
    }));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setScheduled(null);
    setApiError(null);
    const found = problemsOf(form);
    setProblems(found);
    if (found.length > 0) {
      return;
    }
    setSubmitting(true);
    try {
      const { roundId } = await scheduleRound(toRequest(form));
      setScheduled({ roundId, round: await loadRound(roundId) });
    } catch (error) {
      setApiError(
        error instanceof ApiRequestError
          ? error
          : new ApiRequestError('UNEXPECTED_ERROR', 'Ocurrió un error inesperado.', 0),
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <form onSubmit={submit} noValidate>
        <h2>Programar ronda</h2>

        <fieldset>
          <legend>Ronda</legend>
          <Field label="ID de la competencia" value={form.competitionId}
            onChange={(value) => setForm({ ...form, competitionId: value })} />
          <Field label="ID de la categoría" value={form.categoryId}
            onChange={(value) => setForm({ ...form, categoryId: value })} />
          <Field label="ID del desafío" value={form.challengeId}
            onChange={(value) => setForm({ ...form, challengeId: value })} />
          <Field label="Número de ronda" type="number" value={form.ordinal}
            onChange={(value) => setForm({ ...form, ordinal: value })} />
          <Field label="Programada por" value={form.actor}
            onChange={(value) => setForm({ ...form, actor: value })} />
        </fieldset>

        {form.heats.map((heat, index) => (
          <fieldset key={index}>
            <legend>{`Largada ${index + 1}`}</legend>
            <Field label="ID del equipo" value={heat.teamId} onChange={(value) => updateHeat(index, { teamId: value })} />
            <Field label="ID de la pista" value={heat.arenaId} onChange={(value) => updateHeat(index, { arenaId: value })} />
            <Field label="Inicio" type="datetime-local" value={heat.start}
              onChange={(value) => updateHeat(index, { start: value })} />
            <Field label="Duración (minutos)" type="number" value={heat.durationMinutes}
              onChange={(value) => updateHeat(index, { durationMinutes: value })} />
            <Field label="Jueces (separados por coma)" value={heat.judges}
              onChange={(value) => updateHeat(index, { judges: value })} />
            {form.heats.length > 1 && (
              <button type="button"
                onClick={() => setForm({ ...form, heats: form.heats.filter((_, i) => i !== index) })}>
                {`Quitar largada ${index + 1}`}
              </button>
            )}
          </fieldset>
        ))}
        <button type="button" onClick={() => setForm({ ...form, heats: [...form.heats, emptyHeat()] })}>
          Agregar largada
        </button>

        {problems.length > 0 && (
          <div className="problems" role="alert">
            <p>Revisá los datos antes de enviar:</p>
            <ul>
              {problems.map((problem) => (
                <li key={problem}>{problem}</li>
              ))}
            </ul>
          </div>
        )}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Enviando…' : 'Programar ronda'}
        </button>
      </form>

      {apiError &&
        (apiError.code === 'SCHEDULE_CONFLICT' ? (
          <ScheduleConflicts message={apiError.message} conflicts={apiError.conflicts} />
        ) : (
          <section className="result result--error" role="alert">
            <h2>No se pudo programar la ronda</h2>
            <p>{apiError.message}</p>
            <p className="code">Código: {apiError.code}</p>
          </section>
        ))}
      {scheduled?.round && <RoundView round={scheduled.round} />}
      {scheduled && !scheduled.round && (
        <section className="result result--accepted" role="status">
          <h2>Ronda programada</h2>
          <p>
            La ronda quedó programada con el identificador <strong>{scheduled.roundId}</strong>, pero no se pudo
            cargar su detalle.
          </p>
        </section>
      )}
    </>
  );
}

async function loadRound(roundId: string): Promise<Round | null> {
  try {
    return await findRound(roundId);
  } catch {
    return null;
  }
}

function Field(props: { label: string; value: string; onChange: (value: string) => void; type?: string }) {
  return (
    <label>
      {props.label}
      <input type={props.type ?? 'text'} value={props.value}
        onChange={(event) => props.onChange(event.target.value)} />
    </label>
  );
}
