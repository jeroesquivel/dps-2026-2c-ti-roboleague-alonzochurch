import { formatInstant, RUN_STATUS_LABELS } from '../appeals/labels';
import type { Run } from '../appeals/types';

const SOURCE_LABELS: Record<string, string> = { AUTOMATIC: 'Automática', JUDGES: 'Jueces' };

const labelOf = (source: string) => SOURCE_LABELS[source] ?? source;

/** Estado de una corrida tras capturarla o consultarla: completitud, recibos por fuente y datos vigentes. */
export default function RunResultView({ run }: { run: Run }) {
  const { completion } = run;
  const pending = completion.status === 'PENDING';
  return (
    <section className={`result ${pending ? 'result--pending' : 'result--accepted'}`} role="status">
      <h2>
        {`Corrida ${run.runId} `}
        <span className={`badge badge--${pending ? 'provisional' : 'final'}`}>
          {pending ? 'Pendiente' : 'Completa'}
        </span>
      </h2>
      {pending && <p>Faltan las fuentes: {completion.missingSources.map(labelOf).join(', ')}.</p>}
      <dl>
        <dt>Ronda</dt><dd>{run.roundId}</dd>
        <dt>Heat</dt><dd>{run.heatId}</dd>
        <dt>Equipo</dt><dd>{run.teamId}</dd>
        <dt>Desafío</dt><dd>{run.challengeId}</dd>
        <dt>Intento</dt><dd>{run.attemptNumber}</dd>
        <dt>Reglamento</dt><dd>{run.rulebookVersion}</dd>
        <dt>Estado</dt><dd>{`${RUN_STATUS_LABELS[run.status]} (${run.corrections.length} correcciones)`}</dd>
      </dl>

      <h3>Fuentes</h3>
      {completion.expectedSources.length === 0 ? (
        <p>Capturada en una sola operación.</p>
      ) : (
        <ul>
          {completion.expectedSources.map((source) => {
            const receipt = completion.receipts.find((candidate) => candidate.source === source);
            return (
              <li key={source}>
                <strong>{labelOf(source)}:</strong>{' '}
                {receipt ? `recibida por ${receipt.actor} el ${formatInstant(receipt.receivedAt)}` : 'no recibida'}
              </li>
            );
          })}
        </ul>
      )}

      <table>
        <caption>Mediciones actuales</caption>
        <thead><tr><th scope="col">Métrica</th><th scope="col">Valor</th></tr></thead>
        <tbody>{Object.entries(run.currentMeasurements).map(([key, value]) => (
          <tr key={key}><td>{key}</td><td>{value}</td></tr>
        ))}</tbody>
      </table>
      <p>Evaluaciones: {run.evaluations.length === 0 ? 'ninguna' : run.evaluations.map(
        (item) => `${item.judgeId} · ${item.criterion}: ${item.score}`).join('; ')}</p>
      <p>Incidentes: {run.currentIncidents.length === 0 ? 'ninguno' : run.currentIncidents.map(
        (item) => `${item.code} × ${item.occurrences}`).join('; ')}</p>
    </section>
  );
}
