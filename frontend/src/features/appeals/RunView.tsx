import { CONTRIBUTION_LABELS, formatAmount, formatInstant, formatPoints, RUN_STATUS_LABELS } from './labels';
import type { Incident, Run, RunScore } from './types';

function incidentsText(incidents: Incident[]): string {
  return incidents.length === 0 ? 'ninguno' : incidents.map((i) => `${i.code} × ${i.occurrences}`).join(', ');
}

function MeasurementsTable(props: { caption: string; measurements: Record<string, number> }) {
  return (
    <table>
      <caption>{props.caption}</caption>
      <thead>
        <tr>
          <th scope="col">Métrica</th>
          <th scope="col">Valor</th>
        </tr>
      </thead>
      <tbody>
        {Object.entries(props.measurements).map(([key, value]) => (
          <tr key={key}>
            <td>{key}</td>
            <td>{formatAmount(value)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** Resultado vigente de una corrida con su puntaje explicado y, si la hubo, su historia de correcciones. */
export default function RunView({ run, score }: { run: Run; score: RunScore | null }) {
  const corrected = run.corrections.length > 0;
  return (
    <section className="result run" aria-labelledby="run-title">
      <h2 id="run-title">
        {`Corrida ${run.runId} `}
        <span className={`badge badge--${run.status.toLowerCase()}`}>{RUN_STATUS_LABELS[run.status]}</span>
      </h2>
      <dl>
        <dt>Equipo</dt>
        <dd>{run.teamId}</dd>
        <dt>Ronda</dt>
        <dd>{run.roundId}</dd>
        <dt>Intento</dt>
        <dd>{run.attemptNumber}</dd>
        <dt>Reglamento</dt>
        <dd>{run.rulebookVersion}</dd>
        <dt>Capturada</dt>
        <dd>{formatInstant(run.capturedAt)}</dd>
        <dt>Completitud</dt>
        <dd>
          {run.completion.status === 'COMPLETE'
            ? 'Completa'
            : `Pendiente (faltan: ${run.completion.missingSources.join(', ')})`}
        </dd>
      </dl>

      <MeasurementsTable caption="Mediciones vigentes" measurements={run.currentMeasurements} />
      <p>Incidentes vigentes: {incidentsText(run.currentIncidents)}</p>

      {score && (
        <div className="score">
          <h3>
            Puntaje: <strong>{formatPoints(score.total)}</strong>
          </h3>
          <ul>
            {score.contributions.map((contribution, index) => (
              <li key={`${contribution.ruleCode}-${index}`}>
                {`${CONTRIBUTION_LABELS[contribution.kind]} ${contribution.ruleCode}: ${formatPoints(contribution.points)}`}
                <span className="code"> — {contribution.explanation}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {corrected && (
        <details open>
          <summary>{`Historial de correcciones (${run.corrections.length})`}</summary>
          <MeasurementsTable caption="Mediciones originales" measurements={run.originalMeasurements} />
          <p>Incidentes originales: {incidentsText(run.originalIncidents)}</p>
          <ol>
            {run.corrections.map((correction) => (
              <li key={correction.appliedAt}>
                {`${formatInstant(correction.appliedAt)} · ${correction.actor} · apelación ${correction.sourceAppeal}`}
                <span className="code"> — {correction.reason}</span>
              </li>
            ))}
          </ol>
        </details>
      )}
    </section>
  );
}
