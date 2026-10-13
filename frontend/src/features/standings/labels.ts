import type { PublicationStatus, RoundStatus } from './types';

export const STATUS_LABELS: Record<PublicationStatus, string> = {
  PROVISIONAL: 'Provisional',
  FINAL: 'Final',
};

export const ROUND_STATUS_LABELS: Record<RoundStatus, string> = {
  COUNTED: 'Computada',
  DISCARDED: 'Descartada',
};

export const SOURCE_LABELS: Record<string, string> = {
  AUTOMATIC: 'las mediciones automáticas',
  JUDGES: 'la evaluación del jurado',
};

export function formatPoints(points: number): string {
  return points.toFixed(2);
}

/** Instante ISO mostrado en UTC, para que no dependa de la zona del navegador. */
export function formatInstant(iso: string): string {
  return `${iso.slice(0, 16).replace('T', ' ')} UTC`;
}
