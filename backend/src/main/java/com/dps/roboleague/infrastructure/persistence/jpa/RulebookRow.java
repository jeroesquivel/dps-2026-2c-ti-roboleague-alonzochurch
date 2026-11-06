package com.dps.roboleague.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla {@code rulebooks}: las columnas de búsqueda más el agregado serializado en {@code payload}. */
@Entity
@Table(name = "rulebooks")
public class RulebookRow {

    @Id
    @Column(name = "id", nullable = false, length = 200)
    private String id;

    @Column(name = "competition_id", nullable = false, length = 100)
    private String competitionId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    protected RulebookRow() {
    }

    public RulebookRow(String id, String competitionId, int version, String payload) {
        this.id = id;
        this.competitionId = competitionId;
        this.version = version;
        this.payload = payload;
    }

    public String id() {
        return id;
    }

    public String payload() {
        return payload;
    }
}
