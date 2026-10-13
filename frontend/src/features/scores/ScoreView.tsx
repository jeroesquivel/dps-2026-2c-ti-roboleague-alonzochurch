import { CONTRIBUTION_LABELS, formatPoints, MISSING_SOURCE_LABELS, SOURCE_TITLES } from './labels';
import type { BonusCap, Contribution, RunScore } from './types';

function ContributionList({ contributions }: { contributions: Contribution[] }) {
  return (
    <ul>
      {contributions.map((contribution, index) => (
        <li key={`${contribution.ruleCode}-${index}`}
          className={contribution.kind === 'PENALTY' ? 'contribution--penalty' : undefined}>
          {`${CONTRIBUTION_LABELS[contribution.kind]} ${contribution.ruleCode}: ${formatPoints(contribution.points)}`}
          <span className="code"> — {contribution.explanation}</span>
        </li>
      ))}
    </ul>
  );
}

function BonusCapSummary({ bonusCap }: { bonusCap: BonusCap }) {
  return (
    <section className="bonus-cap" aria-label="Tope de bonificaciones">
      <h4>Tope de bonificaciones</h4>
      <dl>
        <dt>Bonificaciones obtenidas</dt>
        <dd>{formatPoints(bonusCap.obtained)}</dd>
        <dt>Tope</dt>
        <dd>{formatPoints(bonusCap.cap)}</dd>
        <dt>Recorte</dt>
        <dd>{formatPoints(bonusCap.trimmed)}</dd>
        <dt>Bonificaciones aplicadas</dt>
        <dd>{formatPoints(bonusCap.applied)}</dd>
      </dl>
    </section>
  );
}

/** Puntaje de una corrida tal como lo calculó el backend: total, contribuciones, tope y fuentes. */
export default function ScoreView({ score }: { score: RunScore }) {
  const meta = `Corrida ${score.runId} · equipo ${score.teamId} · desafío ${score.challengeId} · reglamento ${score.rulebookVersion}`;

  if (score.completionStatus === 'PENDING' || score.total === null || score.contributions === null) {
    return (
      <section className="score score--pending" aria-label="Puntaje">
        <h3>Puntaje pendiente</h3>
        <p className="meta">{meta}</p>
        <p role="status">
          {`La corrida todavía no tiene puntaje: faltan ${score.missingSources
            .map((source) => MISSING_SOURCE_LABELS[source] ?? source)
            .join(' y ')}.`}
        </p>
      </section>
    );
  }

  return (
    <section className="score" aria-label="Puntaje">
      <h3>
        Puntaje: <strong>{formatPoints(score.total)}</strong>
      </h3>
      <p className="meta">{meta}</p>
      {score.bonusCap && <BonusCapSummary bonusCap={score.bonusCap} />}
      {score.bySource ? (
        <>
          {score.bySource.sources.map((group) => (
            <section key={group.source} className="score-source" aria-label={SOURCE_TITLES[group.source] ?? group.source}>
              <h4>{`${SOURCE_TITLES[group.source] ?? group.source}: ${formatPoints(group.subtotal)}`}</h4>
              <ContributionList contributions={group.contributions} />
            </section>
          ))}
          {score.bySource.acrossSources.contributions.length > 0 && (
            <section className="score-source" aria-label="Ajustes del desafío">
              <h4>{`Ajustes del desafío: ${formatPoints(score.bySource.acrossSources.subtotal)}`}</h4>
              <ContributionList contributions={score.bySource.acrossSources.contributions} />
            </section>
          )}
        </>
      ) : (
        <ContributionList contributions={score.contributions} />
      )}
    </section>
  );
}
