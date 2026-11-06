package com.dps.roboleague.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla {@code appeals}: las columnas de búsqueda más el agregado serializado en {@code payload}. */
@Entity
@Table(name = "appeals")
public class AppealRow {

    @Id
    @Column(name = "id", nullable = false, length = 200)
    private String id;

    @Column(name = "run_id", nullable = false, length = 100)
    private String runId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    protected AppealRow() {
    }

    public AppealRow(String id, String runId, String payload) {
        this.id = id;
        this.runId = runId;
        this.payload = payload;
    }

    public String id() {
        return id;
    }

    public String payload() {
        return payload;
    }
}
