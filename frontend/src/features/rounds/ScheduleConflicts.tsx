import type { ScheduleConflict } from '../../api/http';
import { CONFLICT_LABELS } from './formModel';

export default function ScheduleConflicts({ message, conflicts }: { message: string; conflicts: ScheduleConflict[] }) {
  return (
    <section className="result result--rejected" role="alert">
      <h2>Conflicto de agenda</h2>
      <p>{message}</p>
      <p>No se programó ninguna largada de la ronda.</p>
      <ul>
        {conflicts.map((conflict, index) => (
          <li key={`${conflict.type}-${index}`}>
            <strong>{CONFLICT_LABELS[conflict.type] ?? conflict.type}:</strong> {conflict.detail}
          </li>
        ))}
      </ul>
    </section>
  );
}
