import type { RegistrationOutcome } from './types';

const VIOLATION_LABELS: Record<string, string> = {
  AGE_RANGE: 'Edad de los integrantes',
  TEAM_COMPOSITION: 'Composición del equipo',
  ROBOT_CLASS: 'Clase de robot',
  ROBOT_SPECIFICATION: 'Especificación del robot',
  REQUIRED_DOCUMENTS: 'Documentación requerida',
};

export default function RegistrationResult({ outcome }: { outcome: RegistrationOutcome }) {
  if (outcome.status === 'ACCEPTED') {
    return (
      <section className="result result--accepted" role="status">
        <h2>Inscripción aceptada</h2>
        <p>
          El equipo quedó inscripto con el identificador <strong>{outcome.teamId}</strong>.
        </p>
      </section>
    );
  }
  return (
    <section className="result result--rejected" role="alert">
      <h2>Inscripción rechazada</h2>
      <p>
        El equipo quedó registrado con el identificador <strong>{outcome.teamId}</strong>, pero no
        cumple los requisitos del reglamento:
      </p>
      <ul>
        {outcome.violations.map((violation, index) => (
          <li key={`${violation.code}-${index}`}>
            <strong>{VIOLATION_LABELS[violation.code] ?? violation.code}:</strong> {violation.reason}
          </li>
        ))}
      </ul>
    </section>
  );
}
