import type { Round } from './types';

function formatDateTime(iso: string): string {
  return iso.slice(0, 16).replace('T', ' ');
}

export default function RoundView({ round }: { round: Round }) {
  return (
    <section className="result result--accepted" role="status">
      <h2>Ronda programada</h2>
      <p>
        Identificador: <strong>{round.roundId}</strong>
      </p>
      <dl>
        <dt>Competencia</dt>
        <dd>{round.competitionId}</dd>
        <dt>Categoría</dt>
        <dd>{round.categoryId}</dd>
        <dt>Desafío</dt>
        <dd>{round.challengeId}</dd>
        <dt>Ronda</dt>
        <dd>{round.ordinal}</dd>
        <dt>Versión del reglamento</dt>
        <dd>{round.rulebookVersion}</dd>
      </dl>
      <table>
        <caption>Largadas</caption>
        <thead>
          <tr>
            <th scope="col">Equipo</th>
            <th scope="col">Pista</th>
            <th scope="col">Inicio</th>
            <th scope="col">Fin</th>
            <th scope="col">Duración</th>
            <th scope="col">Jueces</th>
          </tr>
        </thead>
        <tbody>
          {round.heats.map((heat) => (
            <tr key={heat.heatId}>
              <td>{heat.teamId}</td>
              <td>{heat.arenaId}</td>
              <td>{formatDateTime(heat.start)}</td>
              <td>{formatDateTime(heat.end)}</td>
              <td>{`${heat.durationMinutes} min`}</td>
              <td>{heat.judges.join(', ')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
