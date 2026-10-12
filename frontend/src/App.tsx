import { useState } from 'react';
import ScheduleRoundForm from './features/rounds/ScheduleRoundForm';
import RegisterTeamForm from './features/teams/RegisterTeamForm';

type Section = 'teams' | 'rounds';

const SECTIONS: { id: Section; label: string }[] = [
  { id: 'teams', label: 'Equipos' },
  { id: 'rounds', label: 'Rondas' },
];

export default function App() {
  const [section, setSection] = useState<Section>('teams');
  return (
    <main className="page">
      <h1>RoboLeague</h1>
      <nav aria-label="Secciones">
        {SECTIONS.map(({ id, label }) => (
          <button key={id} type="button" aria-current={section === id ? 'page' : undefined}
            onClick={() => setSection(id)}>
            {label}
          </button>
        ))}
      </nav>
      {section === 'teams' ? <RegisterTeamForm /> : <ScheduleRoundForm />}
    </main>
  );
}
