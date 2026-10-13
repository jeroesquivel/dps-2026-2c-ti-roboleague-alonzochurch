import { useState } from 'react';
import AppealsPage from './features/appeals/AppealsPage';
import ScorePage from './features/scores/ScorePage';
import ScheduleRoundForm from './features/rounds/ScheduleRoundForm';
import StandingsPage from './features/standings/StandingsPage';
import RegisterTeamForm from './features/teams/RegisterTeamForm';

type Section = 'teams' | 'rounds' | 'standings' | 'appeals' | 'score';

const SECTIONS: { id: Section; label: string }[] = [
  { id: 'teams', label: 'Equipos' },
  { id: 'rounds', label: 'Rondas' },
  { id: 'standings', label: 'Posiciones' },
  { id: 'appeals', label: 'Apelaciones' },
  { id: 'score', label: 'Puntaje' },
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
      {section === 'teams' && <RegisterTeamForm />}
      {section === 'rounds' && <ScheduleRoundForm />}
      {section === 'standings' && <StandingsPage />}
      {section === 'appeals' && <AppealsPage />}
      {section === 'score' && <ScorePage />}
    </main>
  );
}
