package com.dps.roboleague.results.result;

/** Por qué se corrige un resultado: una apelación aceptada o un error de carga detectado por la organización. */
public sealed interface CorrectionOrigin permits AcceptedAppeal, DataEntryError {

    /** Código estable del origen, apto para exponer en contratos externos. */
    String kind();

    String description();
}
