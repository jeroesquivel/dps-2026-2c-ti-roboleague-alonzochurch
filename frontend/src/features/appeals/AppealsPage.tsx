import { useState } from 'react';
import { ApiRequestError } from '../../api/http';
import { findRound } from '../rounds/api';
import type { Round } from '../rounds/types';
import { findRevisions } from '../standings/api';
import StandingsView from '../standings/StandingsView';
import type { Standings } from '../standings/types';
import { findRunScore } from '../scores/api';
import type { RunScore } from '../scores/types';
import { acceptAppeal, findAppeal, findRun, rejectAppeal, submitAppeal } from './api';
import AppealView from './AppealView';
import { submissionProblems } from './formModel';
import ResolutionForm from './ResolutionForm';
import RunView from './RunView';
import type { AcceptAppealRequest, Appeal, RejectAppealRequest, Run, SubmitAppealRequest } from './types';

/** Lo que se observa de las posiciones de la categoría tras aceptar una apelación. */
type Recalculation =
  | { kind: 'created'; standings: Standings }
  | { kind: 'unchanged'; standings: Standings }
  | { kind: 'none' }
  | { kind: 'unknown' };

const NO_SUBMISSION: SubmitAppealRequest = { runId: '', teamId: '', claim: '', actor: '' };

/** Revisiones de posiciones de la categoría de la ronda; vacías si todavía no se generaron. */
async function revisionsOf(round: Round): Promise<Standings[]> {
  try {
    return (await findRevisions(round.competitionId, round.categoryId)).revisions;
  } catch (failure) {
    if (failure instanceof ApiRequestError && failure.code === 'NOT_FOUND') {
      return [];
    }
    throw failure;
  }
}

async function recalculationOf(round: Round | null, before: Standings[] | null): Promise<Recalculation> {
  if (round === null || before === null) {
    return { kind: 'unknown' };
  }
  try {
    const after = await revisionsOf(round);
    const latest = after.at(-1);
    if (latest === undefined) {
      return { kind: 'none' };
    }
    return after.length > before.length ? { kind: 'created', standings: latest } : { kind: 'unchanged', standings: latest };
  } catch {
    return { kind: 'unknown' };
  }
}

export default function AppealsPage() {
  const [runToConsult, setRunToConsult] = useState('');
  const [submission, setSubmission] = useState<SubmitAppealRequest>(NO_SUBMISSION);
  const [appealToConsult, setAppealToConsult] = useState('');
  const [problems, setProblems] = useState<string[]>([]);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [appeal, setAppeal] = useState<Appeal | null>(null);
  const [run, setRun] = useState<Run | null>(null);
  const [score, setScore] = useState<RunScore | null>(null);
  const [recalculation, setRecalculation] = useState<Recalculation | null>(null);
  const [busy, setBusy] = useState(false);

  /** Valida, ejecuta la operación y traduce sus fallos a un mensaje para la pantalla. */
  async function perform(found: string[], operation: () => Promise<void>) {
    setError(null);
    setNotice(null);
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

  async function loadRun(runId: string): Promise<{ run: Run; score: RunScore }> {
    const [loadedRun, loadedScore] = await Promise.all([findRun(runId), findRunScore(runId)]);
    return { run: loadedRun, score: loadedScore };
  }

  /** Muestra la apelación junto con el resultado vigente de su corrida, necesario para corregirlo. */
  async function showAppeal(appealId: string) {
    const loaded = await findAppeal(appealId);
    let result: { run: Run; score: RunScore } | null = null;
    let failure: unknown = null;
    try {
      result = await loadRun(loaded.runId);
    } catch (cause) {
      failure = cause;
    }
    setAppeal(loaded);
    setRun(result?.run ?? null);
    setScore(result?.score ?? null);
    setRecalculation(null);
    if (failure !== null) {
      throw failure;
    }
  }

  const consultRun = () =>
    perform(runToConsult.trim() === '' ? ['El ID de la corrida a consultar es obligatorio.'] : [], async () => {
      const result = await loadRun(runToConsult.trim());
      setRun(result.run);
      setScore(result.score);
      setAppeal(null);
      setRecalculation(null);
      setSubmission((current) => ({ ...current, runId: result.run.runId }));
    });

  const submit = () =>
    perform(submissionProblems(submission), async () => {
      const created = await submitAppeal({
        runId: submission.runId.trim(),
        teamId: submission.teamId.trim(),
        claim: submission.claim.trim(),
        actor: submission.actor.trim(),
      });
      setNotice(`La apelación ${created.appealId} quedó presentada y está pendiente.`);
      await showAppeal(created.appealId);
    });

  const consultAppeal = () =>
    perform(appealToConsult.trim() === '' ? ['El ID de la apelación es obligatorio.'] : [], () =>
      showAppeal(appealToConsult.trim()),
    );

  function accept(request: AcceptAppealRequest) {
    if (appeal === null || run === null) {
      return Promise.resolve();
    }
    return perform([], async () => {
      // Las posiciones previas se consultan para poder informar si la aceptación las recalculó.
      let round: Round | null = null;
      let before: Standings[] | null = null;
      try {
        round = await findRound(run.roundId);
        before = await revisionsOf(round);
      } catch {
        round = null;
      }
      const accepted = await acceptAppeal(appeal.appealId, request);
      setAppeal(accepted);
      setNotice(`La apelación ${accepted.appealId} fue aceptada y la corrida quedó corregida.`);
      const result = await loadRun(accepted.runId);
      setRun(result.run);
      setScore(result.score);
      setRecalculation(await recalculationOf(round, before));
    });
  }

  function reject(request: RejectAppealRequest) {
    if (appeal === null) {
      return Promise.resolve();
    }
    return perform([], async () => {
      const rejected = await rejectAppeal(appeal.appealId, request);
      setAppeal(rejected);
      setRecalculation(null);
      setNotice(`La apelación ${rejected.appealId} fue rechazada; el resultado y las posiciones no cambiaron.`);
    });
  }

  return (
    <>
      <h2>Apelaciones</h2>

      <form onSubmit={(event) => event.preventDefault()} noValidate aria-label="Consultar corrida">
        <fieldset>
          <legend>Consultar una corrida y su puntaje</legend>
          <label>
            Corrida a consultar
            <input type="text" value={runToConsult} onChange={(e) => setRunToConsult(e.target.value)} />
          </label>
        </fieldset>
        <div className="actions">
          <button type="button" disabled={busy} onClick={consultRun}>Consultar corrida</button>
        </div>
      </form>

      <form onSubmit={(event) => event.preventDefault()} noValidate aria-label="Presentar apelación">
        <fieldset>
          <legend>Presentar una apelación</legend>
          <label>
            ID de la corrida
            <input type="text" value={submission.runId}
              onChange={(e) => setSubmission({ ...submission, runId: e.target.value })} />
          </label>
          <label>
            ID del equipo
            <input type="text" value={submission.teamId}
              onChange={(e) => setSubmission({ ...submission, teamId: e.target.value })} />
          </label>
          <label>
            Reclamo
            <input type="text" value={submission.claim}
              onChange={(e) => setSubmission({ ...submission, claim: e.target.value })} />
          </label>
          <label>
            Presentada por
            <input type="text" value={submission.actor}
              onChange={(e) => setSubmission({ ...submission, actor: e.target.value })} />
          </label>
        </fieldset>
        <div className="actions">
          <button type="button" disabled={busy} onClick={submit}>Presentar apelación</button>
        </div>
      </form>

      <form onSubmit={(event) => event.preventDefault()} noValidate aria-label="Consultar apelación">
        <fieldset>
          <legend>Consultar una apelación</legend>
          <label>
            ID de la apelación
            <input type="text" value={appealToConsult} onChange={(e) => setAppealToConsult(e.target.value)} />
          </label>
        </fieldset>
        <div className="actions">
          <button type="button" disabled={busy} onClick={consultAppeal}>Consultar apelación</button>
        </div>
      </form>

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

      {appeal && <AppealView appeal={appeal} />}
      {appeal?.status === 'SUBMITTED' && (
        <ResolutionForm key={`${appeal.appealId}-${run?.corrections.length ?? 'x'}`} run={run} busy={busy}
          onAccept={accept} onReject={reject} />
      )}
      {run && <RunView run={run} score={score} />}
      {recalculation && <RecalculationView recalculation={recalculation} />}
    </>
  );
}

function RecalculationView({ recalculation }: { recalculation: Recalculation }) {
  switch (recalculation.kind) {
    case 'created':
      return (
        <>
          <p className="notice" role="status">
            {`Las posiciones se recalcularon: se creó la revisión ${recalculation.standings.revision}.`}
          </p>
          <StandingsView standings={recalculation.standings} />
        </>
      );
    case 'unchanged':
      return (
        <>
          <p className="notice" role="status">
            {`Las posiciones no tienen una revisión nueva (la última es la ${recalculation.standings.revision}).`}
          </p>
          <StandingsView standings={recalculation.standings} />
        </>
      );
    case 'none':
      return (
        <p className="notice" role="status">
          La categoría todavía no tiene posiciones: la aceptación no las generó.
        </p>
      );
    case 'unknown':
      return (
        <p className="notice" role="status">
          No se pudo consultar si las posiciones se recalcularon; consultalas en la sección Posiciones.
        </p>
      );
  }
}
