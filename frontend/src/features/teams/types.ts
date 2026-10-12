// Contrato HTTP de la API de equipos (ver README del repositorio). No se infiere del código Java.

export type MemberRole = 'COMPETITOR' | 'COACH';

export type DocumentType = 'PARENTAL_CONSENT' | 'INSURANCE' | 'TECHNICAL_SHEET' | 'SAFETY_CHECK';

export interface RegisterTeamRequest {
  competitionId: string;
  categoryId: string;
  teamName: string;
  members: { fullName: string; birthDate: string; role: MemberRole }[];
  robot: {
    name: string;
    robotClass: string;
    weightKg?: number;
    dimensions: { lengthMm?: number; widthMm?: number; heightMm?: number };
  };
  documents: { type: DocumentType; reference: string }[];
  actor: string;
}

export interface Violation {
  code: string;
  reason: string;
}

export interface RegistrationOutcome {
  teamId: string;
  status: 'ACCEPTED' | 'REJECTED';
  violations: Violation[];
}
