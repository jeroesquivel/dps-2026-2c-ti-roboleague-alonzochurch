import { getJson } from '../../api/http';
import type { AuditTrail } from './types';

export function findAuditTrail(subjectId: string): Promise<AuditTrail> {
  return getJson<AuditTrail>(`/audit?subjectId=${encodeURIComponent(subjectId)}`);
}
