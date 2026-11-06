package com.dps.roboleague.infrastructure.persistence.jpa;

/** Las claves compuestas de las tablas, armadas en un solo lugar. */
final class Rows {

    private Rows() {
    }

    static String rulebookKey(String competitionId, int version) {
        return competitionId + "|v" + version;
    }

    static String standingsKey(String competitionId, String categoryId, int revision) {
        return competitionId + "|" + categoryId + "|r" + revision;
    }
}
