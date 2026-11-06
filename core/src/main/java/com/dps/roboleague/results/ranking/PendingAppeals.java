package com.dps.roboleague.results.ranking;

import com.dps.roboleague.shared.RunId;
import java.util.List;

/**
 * Puerto de salida de los resultados: una tabla no se publica mientras haya apelaciones sin resolver sobre sus
 * corridas. Lo define quien lo necesita (los resultados) y lo implementa quien conoce las apelaciones, así los
 * resultados no dependen de las apelaciones.
 */
public interface PendingAppeals {

    /** @throws com.dps.roboleague.shared.ConflictException si alguna de las corridas tiene una apelación pendiente. */
    void requireNoneOn(List<RunId> runs);
}
