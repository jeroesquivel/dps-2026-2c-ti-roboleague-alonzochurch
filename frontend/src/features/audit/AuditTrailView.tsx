import { actionLabel, detailLabel, formatMoment } from './labels';
import type { AuditTrail } from './types';

/** Secuencia de eventos de un sujeto tal como la ordenó el backend; no reconstruye ningún estado. */
export default function AuditTrailView({ trail }: { trail: AuditTrail }) {
  return (
    <section className="result audit" aria-label="Traza de auditoría">
      <h2>Auditoría de {trail.subjectId}</h2>
      {trail.events.length === 0 ? (
        <p role="status">No hay eventos registrados para {trail.subjectId}.</p>
      ) : (
        <ol className="audit-events">
          {trail.events.map((event, index) => (
            <li key={`${event.occurredAt}-${event.action}-${index}`}>
              <h3>
                {actionLabel(event.action)} <span className="code">({event.action})</span>
              </h3>
              <p className="meta">
                <time dateTime={event.occurredAt}>{formatMoment(event.occurredAt)}</time> · por {event.actor}
              </p>
              {Object.keys(event.details).length > 0 && (
                <dl>
                  {Object.entries(event.details).map(([key, value]) => (
                    <div className="audit-detail" key={key}>
                      <dt>{detailLabel(key)}</dt>
                      <dd>{value}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
