package com.dps.roboleague.results.result;

/** La corrección repara un error al cargar el resultado; no hubo reclamo de ningún equipo. */
public record DataEntryError() implements CorrectionOrigin {

    public static final String KIND = "DATA_ENTRY_ERROR";

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String description() {
        return "data entry error";
    }
}
