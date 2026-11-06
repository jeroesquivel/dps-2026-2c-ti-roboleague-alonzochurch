package com.dps.roboleague.appeals.application;

import com.dps.roboleague.appeals.AppealRepository;
import com.dps.roboleague.appeals.Appeals;
import com.dps.roboleague.results.ranking.PendingAppeals;
import com.dps.roboleague.shared.RunId;
import java.util.List;

/** Responde, con el repositorio de apelaciones, si alguna de las corridas tiene una apelación sin resolver. */
public final class AppealsPendingOnRuns implements PendingAppeals {

    private final AppealRepository appeals;

    public AppealsPendingOnRuns(AppealRepository appeals) {
        this.appeals = appeals;
    }

    @Override
    public void requireNoneOn(List<RunId> runs) {
        new Appeals(runs.stream().flatMap(run -> appeals.findByRun(run).stream()).toList()).requireNonePending();
    }
}
