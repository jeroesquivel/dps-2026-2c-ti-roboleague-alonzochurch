import { useState } from 'react';
import AppealsPage from './features/appeals/AppealsPage';
import AuditPage from './features/audit/AuditPage';
import EventPage from './features/event/EventPage';
import ScorePage from './features/scores/ScorePage';
import ResultsPage from './features/results/ResultsPage';
import ScheduleRoundForm from './features/rounds/ScheduleRoundForm';
import StandingsPage from './features/standings/StandingsPage';
import RegisterTeamForm from './features/teams/RegisterTeamForm';

type Section = 'event' | 'teams' | 'rounds' | 'results' | 'standings' | 'appeals' | 'score' | 'audit';

const SECTIONS: { id: Section; label: string }[] = [
  { id: 'event', label: 'Evento' },
  { id: 'teams', label: 'Equipos' },
  { id: 'rounds', label: 'Rondas' },
  { id: 'results', label: 'Resultados' },
  { id: 'standings', label: 'Posiciones' },
  { id: 'appeals', label: 'Apelaciones' },
  { id: 'score', label: 'Puntaje' },
  { id: 'audit', label: 'Auditoría' },
];

export default function App() {
  const [section, setSection] = useState<Section>('event');
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
      {section === 'event' && <EventPage />}
      {section === 'teams' && <RegisterTeamForm />}
      {section === 'rounds' && <ScheduleRoundForm />}
      {section === 'results' && <ResultsPage />}
      {section === 'standings' && <StandingsPage />}
      {section === 'appeals' && <AppealsPage />}
      {section === 'score' && <ScorePage />}
      {section === 'audit' && <AuditPage />}
    </main>
  );
}
