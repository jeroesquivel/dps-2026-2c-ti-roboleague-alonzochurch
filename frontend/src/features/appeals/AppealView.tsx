import { APPEAL_STATUS_LABELS, formatInstant } from './labels';
import type { Appeal } from './types';

export default function AppealView({ appeal }: { appeal: Appeal }) {
  return (
    <section className={`result result--${appeal.status.toLowerCase()}`} aria-labelledby="appeal-title">
      <h2 id="appeal-title">
        {`Apelación ${appeal.appealId} `}
        <span className={`badge badge--${appeal.status.toLowerCase()}`}>{APPEAL_STATUS_LABELS[appeal.status]}</span>
      </h2>
      <dl>
        <dt>Corrida</dt>
        <dd>{appeal.runId}</dd>
        <dt>Equipo</dt>
        <dd>{appeal.teamId}</dd>
        <dt>Presentada</dt>
        <dd>{formatInstant(appeal.submittedAt)}</dd>
        <dt>Reclamo</dt>
        <dd>{appeal.claim}</dd>
        {appeal.decision && (
          <>
            <dt>Revisor</dt>
            <dd>{appeal.decision.reviewer}</dd>
            <dt>Resuelta</dt>
            <dd>{formatInstant(appeal.decision.decidedAt)}</dd>
            <dt>Fundamento</dt>
            <dd>{appeal.decision.rationale}</dd>
          </>
        )}
      </dl>
    </section>
  );
}
