package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.shared.Identifier;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.util.List;

/** La bitácora solo se agrega: cada evento es una fila nueva, y se consulta por el sujeto sobre el que ocurrió. */
public final class JpaAuditLog implements AuditLog {

    private final AuditEventRows rows;
    private final AggregateJson json;

    public JpaAuditLog(AuditEventRows rows, AggregateJson json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void record(AuditEvent event) {
        rows.save(new AuditEventRow(event.subject().getClass().getName(), event.subject().value(),
                json.write(event)));
    }

    @Override
    public List<AuditEvent> findBySubject(Identifier subject) {
        return rows.findBySubjectTypeAndSubjectIdOrderByIdAsc(subject.getClass().getName(), subject.value())
                .stream()
                .map(row -> json.read(row.payload(), AuditEvent.class))
                .toList();
    }
}
