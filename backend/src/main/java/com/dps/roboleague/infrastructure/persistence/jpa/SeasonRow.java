package com.dps.roboleague.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la tabla {@code seasons}: las columnas de búsqueda más el agregado serializado en {@code payload}. */
@Entity
@Table(name = "seasons")
public class SeasonRow {

    @Id
    @Column(name = "id", nullable = false, length = 200)
    private String id;

    @Column(name = "sort_order", nullable = false)
    private long sortOrder;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    protected SeasonRow() {
    }

    public SeasonRow(String id, long sortOrder, String payload) {
        this.id = id;
        this.sortOrder = sortOrder;
        this.payload = payload;
    }

    public String id() {
        return id;
    }

    public long sortOrder() {
        return sortOrder;
    }

    public String payload() {
        return payload;
    }
}
