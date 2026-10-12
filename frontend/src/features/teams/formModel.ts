import type { DocumentType, MemberRole, RegisterTeamRequest } from './types';

export interface MemberDraft {
  fullName: string;
  birthDate: string;
  role: MemberRole;
}

export interface DocumentDraft {
  type: DocumentType;
  reference: string;
}

export interface FormState {
  competitionId: string;
  categoryId: string;
  teamName: string;
  actor: string;
  members: MemberDraft[];
  robot: {
    name: string;
    robotClass: string;
    weightKg: string;
    lengthMm: string;
    widthMm: string;
    heightMm: string;
  };
  documents: DocumentDraft[];
}

export const ROLE_LABELS: Record<MemberRole, string> = {
  COMPETITOR: 'Competidor',
  COACH: 'Entrenador',
};

export const DOCUMENT_LABELS: Record<DocumentType, string> = {
  PARENTAL_CONSENT: 'Consentimiento parental',
  INSURANCE: 'Seguro',
  TECHNICAL_SHEET: 'Ficha técnica',
  SAFETY_CHECK: 'Control de seguridad',
};

export function emptyMember(): MemberDraft {
  return { fullName: '', birthDate: '', role: 'COMPETITOR' };
}

export function emptyDocument(): DocumentDraft {
  return { type: 'PARENTAL_CONSENT', reference: '' };
}

export function emptyForm(): FormState {
  return {
    competitionId: '',
    categoryId: '',
    teamName: '',
    actor: '',
    members: [emptyMember()],
    robot: { name: '', robotClass: '', weightKg: '', lengthMm: '', widthMm: '', heightMm: '' },
    documents: [],
  };
}

/**
 * Valida únicamente presencia y formato para mejorar la experiencia. La elegibilidad y las
 * invariantes del negocio las decide siempre el backend.
 */
export function problemsOf(form: FormState): string[] {
  const problems: string[] = [];
  const required = (value: string, message: string) => {
    if (value.trim() === '') {
      problems.push(message);
    }
  };
  const numeric = (value: string, message: string) => {
    if (value.trim() === '' || !Number.isFinite(Number(value))) {
      problems.push(message);
    }
  };

  required(form.competitionId, 'Ingresá el ID de la competencia.');
  required(form.categoryId, 'Ingresá el ID de la categoría.');
  required(form.teamName, 'Ingresá el nombre del equipo.');
  required(form.actor, 'Ingresá quién registra el equipo.');
  form.members.forEach((member, index) => {
    required(member.fullName, `Integrante ${index + 1}: ingresá el nombre completo.`);
    required(member.birthDate, `Integrante ${index + 1}: ingresá la fecha de nacimiento.`);
  });
  required(form.robot.name, 'Robot: ingresá el nombre.');
  required(form.robot.robotClass, 'Robot: ingresá la clase.');
  numeric(form.robot.weightKg, 'Robot: el peso debe ser un número.');
  numeric(form.robot.lengthMm, 'Robot: el largo debe ser un número.');
  numeric(form.robot.widthMm, 'Robot: el ancho debe ser un número.');
  numeric(form.robot.heightMm, 'Robot: el alto debe ser un número.');
  form.documents.forEach((document, index) => {
    required(document.reference, `Documento ${index + 1}: ingresá la referencia.`);
  });
  return problems;
}

export function toRequest(form: FormState): RegisterTeamRequest {
  return {
    competitionId: form.competitionId.trim(),
    categoryId: form.categoryId.trim(),
    teamName: form.teamName.trim(),
    members: form.members.map((member) => ({
      fullName: member.fullName.trim(),
      birthDate: member.birthDate,
      role: member.role,
    })),
    robot: {
      name: form.robot.name.trim(),
      robotClass: form.robot.robotClass.trim(),
      weightKg: Number(form.robot.weightKg),
      dimensions: {
        lengthMm: Number(form.robot.lengthMm),
        widthMm: Number(form.robot.widthMm),
        heightMm: Number(form.robot.heightMm),
      },
    },
    documents: form.documents.map((document) => ({
      type: document.type,
      reference: document.reference.trim(),
    })),
    actor: form.actor.trim(),
  };
}
