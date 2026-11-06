package com.dps.roboleague.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla {@code audit_events}: el evento serializado y el sujeto sobre el que se consulta. */
@Entity
@Table(name = "audit_events")
public class AuditEventRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "subject_type", nullable = false, length = 200)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, length = 100)
    private String subjectId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    protected AuditEventRow() {
    }

    public AuditEventRow(String subjectType, String subjectId, String payload) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.payload = payload;
    }

    public String payload() {
        return payload;
    }
}
