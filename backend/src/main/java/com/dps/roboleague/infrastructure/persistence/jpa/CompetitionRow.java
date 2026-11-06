package com.dps.roboleague.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla {@code competitions}: las columnas de búsqueda más el agregado serializado en {@code payload}. */
@Entity
@Table(name = "competitions")
public class CompetitionRow {

    @Id
    @Column(name = "id", nullable = false, length = 200)
    private String id;

    @Column(name = "season_id", nullable = false, length = 100)
    private String seasonId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    protected CompetitionRow() {
    }

    public CompetitionRow(String id, String seasonId, String payload) {
        this.id = id;
        this.seasonId = seasonId;
        this.payload = payload;
    }

    public String id() {
        return id;
    }

    public String payload() {
        return payload;
    }
}
