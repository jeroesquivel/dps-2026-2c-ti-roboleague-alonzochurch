import { useCallback, useEffect, useState } from 'react';
import type { ApiRequestError } from '../../api/http';
import { findCompetition, publishRulebook } from './api';
import Field from './Field';
import { DEFAULT_PRESET, publishProblems, publishRequest } from './formModel';
import { presetText } from './presets';
import { ApiProblem, ProblemList, asApiError } from './Problems';
import RulebookEditor from './RulebookEditor';
import type { Competition } from './types';

interface Props {
  actor: string;
  /** Competencia obtenida en el paso anterior; se carga sola pero se puede cambiar. */
  competitionId: string;
  /** Cambia cada vez que se crea una competencia, para consultarla sin pasos manuales. */
  loadSignal: number;
}

export default function CompetitionPanel({ actor, competitionId, loadSignal }: Props) {
  const [id, setId] = useState(competitionId);
  const [competition, setCompetition] = useState<Competition | null>(null);
  const [rulebook, setRulebook] = useState(() => presetText(DEFAULT_PRESET));
  const [problems, setProblems] = useState<string[]>([]);
  const [error, setError] = useState<ApiRequestError | null>(null);
  const [published, setPublished] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (target: string) => {
    setError(null);
    try {
      setCompetition(await findCompetition(target.trim()));
    } catch (failure) {
      setCompetition(null);
      setError(asApiError(failure));
    }
  }, []);

  useEffect(() => {
    setId(competitionId);
    if (loadSignal > 0 && competitionId !== '') {
      setPublished(null);
      void load(competitionId);
    }
  }, [competitionId, loadSignal, load]);

  async function consult() {
    setPublished(null);
    if (id.trim() === '') {
      setProblems(['El ID de la competencia es obligatorio.']);
      return;
    }
    setProblems([]);
    setBusy(true);
    await load(id);
    setBusy(false);
  }

  async function publish() {
    setPublished(null);
    setError(null);
    const found = publishProblems(id, rulebook, actor);
    setProblems(found);
    if (found.length > 0) {
      return;
    }
    setBusy(true);
    try {
      const result = await publishRulebook(id.trim(), publishRequest(rulebook, actor));
      setPublished(result.rulebookVersion);
      await load(id);
    } catch (failure) {
      setError(asApiError(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-label="Consulta y nuevas versiones">
      <h3>3. Consultar la competencia y publicar nuevas versiones</h3>
      <fieldset>
        <legend>Competencia</legend>
        <Field label="ID de la competencia" value={id} onChange={setId} />
        <div className="actions">
          <button type="button" disabled={busy} onClick={consult}>Consultar competencia</button>
        </div>
      </fieldset>

      {competition && (
        <section className="result" aria-label="Competencia consultada">
          <h4>{competition.name}</h4>
          <dl>
            <dt>ID</dt>
            <dd>{competition.competitionId}</dd>
            <dt>Período</dt>
            <dd>{competition.period.start} a {competition.period.end}</dd>
            <dt>Reglamento activo</dt>
            <dd>{competition.rulebookVersion}</dd>
          </dl>
          <table>
            <caption>Categorías</caption>
            <thead>
              <tr><th>Categoría</th><th>ID</th><th>Edades</th><th>Clase de robot</th></tr>
            </thead>
            <tbody>
              {competition.categories.map((category) => (
                <tr key={category.categoryId}>
                  <td>{category.name}</td>
                  <td>{category.categoryId}</td>
                  <td>{category.minimumAge} a {category.maximumAge}</td>
                  <td>{category.robotClass}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <RulebookEditor title="Nuevo reglamento" value={rulebook} onChange={setRulebook} />
      <ProblemList problems={problems} />
      <button type="button" disabled={busy} onClick={publish}>Publicar nueva versión</button>
      {published && (
        <p className="notice" role="status">
          Versión publicada: <strong>{published}</strong>. Las rondas ya programadas conservan su versión.
        </p>
      )}
      {error && <ApiProblem title="No se pudo completar la operación" error={error} />}
    </section>
  );
}
