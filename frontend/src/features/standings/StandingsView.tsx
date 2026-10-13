import { formatInstant, formatPoints, ROUND_STATUS_LABELS, SOURCE_LABELS, STATUS_LABELS } from './labels';
import type { PendingRun, StandingEntry, Standings } from './types';

export default function StandingsView({ standings }: { standings: Standings }) {
  return (
    <section className="result standings" aria-labelledby="standings-title">
      <h2 id="standings-title">
        {`Posiciones, revisión ${standings.revision} `}
        <span className={`badge badge--${standings.status.toLowerCase()}`}>{STATUS_LABELS[standings.status]}</span>
      </h2>
      <p className="meta">
        Generada el {formatInstant(standings.generatedAt)} · Reglamento {standings.rulebookVersion}
      </p>

      {standings.pendingRuns.length > 0 && <PendingRuns runs={standings.pendingRuns} />}

      {standings.entries.length === 0 ? (
        <p>Todavía no hay equipos con resultados puntuables.</p>
      ) : (
        <table>
          <caption>Tabla de posiciones</caption>
          <thead>
            <tr>
              <th scope="col">Posición</th>
              <th scope="col">Equipo</th>
              <th scope="col">Puntos</th>
              <th scope="col">Desempates</th>
              <th scope="col">Explicación</th>
            </tr>
          </thead>
          <tbody>
            {standings.entries.map((entry) => (
              <tr key={entry.teamId}>
                <td>{entry.position}</td>
                <td>
                  {entry.teamName} <span className="code">({entry.teamId})</span>
                </td>
                <td>{formatPoints(entry.totalPoints)}</td>
                <td>{entry.appliedTiebreaks.map((tiebreak) => tiebreak.description).join(', ') || '—'}</td>
                <td>
                  <Explanation entry={entry} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function PendingRuns({ runs }: { runs: PendingRun[] }) {
  return (
    <section className="pending" aria-labelledby="pending-title">
      <h3 id="pending-title">Corridas pendientes</h3>
      <p>No aportan puntaje hasta que lleguen todos sus resultados.</p>
      <ul>
        {runs.map((run) => (
          <li key={run.runId}>
            <strong>{run.teamName}</strong>, ronda {run.roundId}, intento {run.attemptNumber}: falta{' '}
            {run.missingSources.map((source) => SOURCE_LABELS[source] ?? source).join(' y ')}.
          </li>
        ))}
      </ul>
    </section>
  );
}

function Explanation({ entry }: { entry: StandingEntry }) {
  return (
    <details>
      <summary>{`Ver explicación de ${entry.teamName}`}</summary>
      <ul>
        {entry.explanation.subtotals.map((subtotal) => (
          <li key={subtotal.code}>
            {subtotal.description}: <strong>{formatPoints(subtotal.points)}</strong>
            {subtotal.rounds.length > 0 && (
              <ul>
                {subtotal.rounds.map((round) => (
                  <li key={round.ordinal}>
                    Ronda {round.ordinal}: {formatPoints(round.points)} ({ROUND_STATUS_LABELS[round.status]}) —{' '}
                    {round.reason}
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
    </details>
  );
}
