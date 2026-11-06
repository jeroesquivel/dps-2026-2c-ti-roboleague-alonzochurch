package com.dps.roboleague.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla {@code standings}: las columnas de búsqueda más el agregado serializado en {@code payload}. */
@Entity
@Table(name = "standings")
public class StandingsRow {

    @Id
    @Column(name = "id", nullable = false, length = 200)
    private String id;

    @Column(name = "competition_id", nullable = false, length = 100)
    private String competitionId;

    @Column(name = "category_id", nullable = false, length = 100)
    private String categoryId;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    protected StandingsRow() {
    }

    public StandingsRow(String id, String competitionId, String categoryId, int revision, String payload) {
        this.id = id;
        this.competitionId = competitionId;
        this.categoryId = categoryId;
        this.revision = revision;
        this.payload = payload;
    }

    public String id() {
        return id;
    }

    public String payload() {
        return payload;
    }
}
