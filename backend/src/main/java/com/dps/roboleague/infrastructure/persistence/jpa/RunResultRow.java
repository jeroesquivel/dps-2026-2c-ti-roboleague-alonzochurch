package com.dps.roboleague.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla {@code run_results}: las columnas de búsqueda más el agregado serializado en {@code payload}. */
@Entity
@Table(name = "run_results")
public class RunResultRow {

    @Id
    @Column(name = "id", nullable = false, length = 200)
    private String id;

    @Column(name = "round_id", nullable = false, length = 100)
    private String roundId;

    @Column(name = "team_id", nullable = false, length = 100)
    private String teamId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    protected RunResultRow() {
    }

    public RunResultRow(String id, String roundId, String teamId, int attemptNumber, String payload) {
        this.id = id;
        this.roundId = roundId;
        this.teamId = teamId;
        this.attemptNumber = attemptNumber;
        this.payload = payload;
    }

    public String id() {
        return id;
    }

    public String payload() {
        return payload;
    }
}
