import type { RunResult } from './types';

const SOURCE_LABELS = { AUTOMATIC: 'Automática', JUDGES: 'Jueces' } as const;

export default function RunResultView({ result }: { result: RunResult }) {
  const pending = result.completionStatus === 'PENDING';
  return (
    <section className={`result ${pending ? 'result--pending' : 'result--accepted'}`} role="status">
      <h2>
        {`Corrida ${result.runId} `}
        <span className={`badge badge--${pending ? 'provisional' : 'final'}`}>
          {pending ? 'Pendiente' : 'Completa'}
        </span>
      </h2>
      {pending && <p>Faltan las fuentes: {result.missingSources.map((source) => SOURCE_LABELS[source]).join(', ')}.</p>}
      <dl>
        <dt>Ronda</dt><dd>{result.roundId}</dd>
        <dt>Heat</dt><dd>{result.heatId}</dd>
        <dt>Equipo</dt><dd>{result.teamId}</dd>
        <dt>Desafío</dt><dd>{result.challengeId}</dd>
        <dt>Intento</dt><dd>{result.attemptNumber}</dd>
        <dt>Reglamento</dt><dd>{`v${result.rulebookVersion}`}</dd>
        <dt>Correcciones</dt><dd>{`${result.correctionStatus} (${result.correctionCount})`}</dd>
      </dl>

      <h3>Fuentes</h3>
      <ul>
        {result.sources.map((source) => (
          <li key={source.source}>
            <strong>{SOURCE_LABELS[source.source]}:</strong>{' '}
            {source.received ? `recibida por ${source.actor} el ${formatInstant(source.receivedAt!)}` : 'no recibida'}
          </li>
        ))}
      </ul>

      <table>
        <caption>Mediciones actuales</caption>
        <thead><tr><th scope="col">Métrica</th><th scope="col">Valor</th></tr></thead>
        <tbody>{Object.entries(result.measurements).map(([key, value]) => (
          <tr key={key}><td>{key}</td><td>{value}</td></tr>
        ))}</tbody>
      </table>
      <p>Evaluaciones: {result.evaluations.length === 0 ? 'ninguna' : result.evaluations.map(
        (item) => `${item.judgeId} · ${item.criterion}: ${item.score}`).join('; ')}</p>
      <p>Incidentes: {result.incidents.length === 0 ? 'ninguno' : result.incidents.map(
        (item) => `${item.code} × ${item.occurrences}`).join('; ')}</p>
    </section>
  );
}

function formatInstant(value: string): string {
  return new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'UTC' })
    .format(new Date(value));
}
