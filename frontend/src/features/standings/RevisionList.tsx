import { formatInstant, STATUS_LABELS } from './labels';
import type { Standings } from './types';

interface Props {
  revisions: Standings[];
  selected: number;
  onSelect: (revision: number) => void;
}

export default function RevisionList({ revisions, selected, onSelect }: Props) {
  return (
    <nav className="revisions" aria-label="Revisiones">
      {revisions.map((standings) => (
        <button key={standings.revision} type="button"
          aria-current={standings.revision === selected ? 'true' : undefined}
          onClick={() => onSelect(standings.revision)}>
          {`Revisión ${standings.revision} · ${STATUS_LABELS[standings.status]} · ${formatInstant(standings.generatedAt)}`}
        </button>
      ))}
    </nav>
  );
}
