import { useState } from 'react';
import { ApiRequestError, findTeam } from './api';
import { DOCUMENT_LABELS, ROLE_LABELS } from './formModel';
import RegistrationResult from './RegistrationResult';
import type { TeamRegistration } from './types';

/** Consulta de una inscripción por su ID: datos, estado y violaciones de elegibilidad. */
export default function TeamLookup() {
  const [teamId, setTeamId] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [team, setTeam] = useState<TeamRegistration | null>(null);
  const [busy, setBusy] = useState(false);

  async function consult() {
    setError(null);
    if (teamId.trim() === '') {
      setProblem('El ID del equipo es obligatorio.');
      return;
    }
    setProblem(null);
    setBusy(true);
    try {
      setTeam(await findTeam(teamId.trim()));
    } catch (failure) {
      setTeam(null);
      setError(failure instanceof ApiRequestError
        ? failure
        : new ApiRequestError('UNEXPECTED_ERROR', 'Ocurrió un error inesperado.', 0));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="lookup" aria-labelledby="team-lookup-title">
      <h2 id="team-lookup-title">Consultar equipo</h2>
      <label>
        ID del equipo a consultar
        <input type="text" value={teamId} onChange={(event) => setTeamId(event.target.value)} />
      </label>
      <button type="button" disabled={busy} onClick={consult}>Consultar equipo</button>
      {problem && <div className="problems" role="alert"><p>{problem}</p></div>}
      {error && (
        <section className="result result--error" role="alert">
          <h2>No se pudo consultar el equipo</h2>
          <p>{error.message}</p>
          <p className="code">Código: {error.code}</p>
        </section>
      )}
      {team && (
        <>
          <RegistrationResult outcome={{ teamId: team.teamId, status: team.status === 'ACCEPTED' ? 'ACCEPTED' : 'REJECTED', violations: team.violations }} />
          <dl>
            <dt>Equipo</dt><dd>{team.name}</dd>
            <dt>Competencia</dt><dd>{team.competitionId}</dd>
            <dt>Categoría</dt><dd>{team.categoryId}</dd>
            <dt>Robot</dt>
            <dd>{`${team.robot.name} (${team.robot.robotClass}, ${team.robot.weightKg} kg, ${team.robot.dimensions.lengthMm}×${team.robot.dimensions.widthMm}×${team.robot.dimensions.heightMm} mm)`}</dd>
            <dt>Integrantes</dt>
            <dd>{team.members.map((m) => `${m.fullName} (${ROLE_LABELS[m.role]}, ${m.birthDate})`).join('; ')}</dd>
            <dt>Documentación</dt>
            <dd>{team.documentTypes.map((d) => DOCUMENT_LABELS[d]).join(', ') || 'ninguna'}</dd>
          </dl>
        </>
      )}
    </section>
  );
}
