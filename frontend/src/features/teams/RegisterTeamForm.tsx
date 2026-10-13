import { useState, type FormEvent } from 'react';
import { ApiRequestError, registerTeam } from './api';
import {
  DOCUMENT_LABELS,
  ROLE_LABELS,
  emptyDocument,
  emptyForm,
  emptyMember,
  problemsOf,
  toRequest,
  type DocumentDraft,
  type FormState,
  type MemberDraft,
} from './formModel';
import RegistrationResult from './RegistrationResult';
import TeamLookup from './TeamLookup';
import type { DocumentType, MemberRole, RegistrationOutcome } from './types';

type RobotField = keyof FormState['robot'];

export default function RegisterTeamForm() {
  const [form, setForm] = useState<FormState>(emptyForm);
  const [problems, setProblems] = useState<string[]>([]);
  const [apiError, setApiError] = useState<ApiRequestError | null>(null);
  const [outcome, setOutcome] = useState<RegistrationOutcome | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const updateMember = (index: number, change: Partial<MemberDraft>) =>
    setForm((current) => ({
      ...current,
      members: current.members.map((member, i) => (i === index ? { ...member, ...change } : member)),
    }));

  const updateDocument = (index: number, change: Partial<DocumentDraft>) =>
    setForm((current) => ({
      ...current,
      documents: current.documents.map((doc, i) => (i === index ? { ...doc, ...change } : doc)),
    }));

  const updateRobot = (field: RobotField, value: string) =>
    setForm((current) => ({ ...current, robot: { ...current.robot, [field]: value } }));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setOutcome(null);
    setApiError(null);
    const found = problemsOf(form);
    setProblems(found);
    if (found.length > 0) {
      return;
    }
    setSubmitting(true);
    try {
      setOutcome(await registerTeam(toRequest(form)));
    } catch (error) {
      setApiError(
        error instanceof ApiRequestError
          ? error
          : new ApiRequestError('UNEXPECTED_ERROR', 'Ocurrió un error inesperado.', 0),
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <form onSubmit={submit} noValidate>
        <h2>Registrar equipo</h2>

        <fieldset>
          <legend>Inscripción</legend>
          <Field label="ID de la competencia" value={form.competitionId}
            onChange={(value) => setForm({ ...form, competitionId: value })} />
          <Field label="ID de la categoría" value={form.categoryId}
            onChange={(value) => setForm({ ...form, categoryId: value })} />
          <Field label="Nombre del equipo" value={form.teamName}
            onChange={(value) => setForm({ ...form, teamName: value })} />
          <Field label="Registrado por" value={form.actor}
            onChange={(value) => setForm({ ...form, actor: value })} />
        </fieldset>

        {form.members.map((member, index) => (
          <fieldset key={index}>
            <legend>{`Integrante ${index + 1}`}</legend>
            <Field label="Nombre completo" value={member.fullName}
              onChange={(value) => updateMember(index, { fullName: value })} />
            <Field label="Fecha de nacimiento" type="date" value={member.birthDate}
              onChange={(value) => updateMember(index, { birthDate: value })} />
            <label>
              Rol
              <select value={member.role}
                onChange={(event) => updateMember(index, { role: event.target.value as MemberRole })}>
                {Object.entries(ROLE_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
            {form.members.length > 1 && (
              <button type="button"
                onClick={() => setForm({ ...form, members: form.members.filter((_, i) => i !== index) })}>
                {`Quitar integrante ${index + 1}`}
              </button>
            )}
          </fieldset>
        ))}
        <button type="button" onClick={() => setForm({ ...form, members: [...form.members, emptyMember()] })}>
          Agregar integrante
        </button>

        <fieldset>
          <legend>Robot</legend>
          <Field label="Nombre del robot" value={form.robot.name} onChange={(value) => updateRobot('name', value)} />
          <Field label="Clase del robot" value={form.robot.robotClass}
            onChange={(value) => updateRobot('robotClass', value)} />
          <Field label="Peso (kg)" type="number" value={form.robot.weightKg}
            onChange={(value) => updateRobot('weightKg', value)} />
          <Field label="Largo (mm)" type="number" value={form.robot.lengthMm}
            onChange={(value) => updateRobot('lengthMm', value)} />
          <Field label="Ancho (mm)" type="number" value={form.robot.widthMm}
            onChange={(value) => updateRobot('widthMm', value)} />
          <Field label="Alto (mm)" type="number" value={form.robot.heightMm}
            onChange={(value) => updateRobot('heightMm', value)} />
        </fieldset>

        {form.documents.map((document, index) => (
          <fieldset key={index}>
            <legend>{`Documento ${index + 1}`}</legend>
            <label>
              Tipo de documento
              <select value={document.type}
                onChange={(event) => updateDocument(index, { type: event.target.value as DocumentType })}>
                {Object.entries(DOCUMENT_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
            <Field label="Referencia" value={document.reference}
              onChange={(value) => updateDocument(index, { reference: value })} />
            <button type="button"
              onClick={() => setForm({ ...form, documents: form.documents.filter((_, i) => i !== index) })}>
              {`Quitar documento ${index + 1}`}
            </button>
          </fieldset>
        ))}
        <button type="button" onClick={() => setForm({ ...form, documents: [...form.documents, emptyDocument()] })}>
          Agregar documento
        </button>

        {problems.length > 0 && (
          <div className="problems" role="alert">
            <p>Revisá los datos antes de enviar:</p>
            <ul>
              {problems.map((problem) => (
                <li key={problem}>{problem}</li>
              ))}
            </ul>
          </div>
        )}

        <button type="submit" disabled={submitting}>
          {submitting ? 'Enviando…' : 'Registrar equipo'}
        </button>
      </form>

      {apiError && (
        <section className="result result--error" role="alert">
          <h2>No se pudo registrar el equipo</h2>
          <p>{apiError.message}</p>
          <p className="code">Código: {apiError.code}</p>
        </section>
      )}
      {outcome && <RegistrationResult outcome={outcome} />}
      <TeamLookup />
    </>
  );
}

function Field(props: { label: string; value: string; onChange: (value: string) => void; type?: string }) {
  return (
    <label>
      {props.label}
      <input type={props.type ?? 'text'} value={props.value}
        onChange={(event) => props.onChange(event.target.value)} />
    </label>
  );
}
