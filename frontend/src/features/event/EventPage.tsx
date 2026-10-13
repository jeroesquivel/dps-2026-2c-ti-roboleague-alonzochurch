import { useState } from 'react';
import CompetitionForm from './CompetitionForm';
import CompetitionPanel from './CompetitionPanel';
import Field from './Field';
import { DEFAULT_ACTOR } from './formModel';
import SeasonForm from './SeasonForm';

/**
 * Configuración del evento en orden: temporada, competencia con su reglamento inicial y consulta de
 * la competencia con sus nuevas versiones. Los identificadores que devuelve cada paso se cargan solos
 * en el siguiente.
 */
export default function EventPage() {
  const [actor, setActor] = useState(DEFAULT_ACTOR);
  const [seasonId, setSeasonId] = useState('');
  const [competitionId, setCompetitionId] = useState('');
  const [loadSignal, setLoadSignal] = useState(0);

  return (
    <>
      <h2>Configurar el evento</h2>
      <fieldset>
        <legend>Organizador</legend>
        <Field label="Actor de la configuración" value={actor} onChange={setActor} />
      </fieldset>
      <SeasonForm actor={actor} onCreated={setSeasonId} />
      <CompetitionForm actor={actor} seasonId={seasonId}
        onCreated={(created) => {
          setCompetitionId(created.competitionId);
          setLoadSignal((signal) => signal + 1);
        }} />
      <CompetitionPanel actor={actor} competitionId={competitionId} loadSignal={loadSignal} />
    </>
  );
}
