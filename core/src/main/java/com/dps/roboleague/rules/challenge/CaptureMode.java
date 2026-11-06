package com.dps.roboleague.rules.challenge;

import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.rules.scoring.ScoringRule;
import java.util.List;
import java.util.Optional;

/** Cómo llegan los datos de una corrida: en una sola captura, o de dos fuentes (automática y jurado). */
public sealed interface CaptureMode permits SingleCapture, MixedSources {

    static CaptureMode single() {
        return new SingleCapture();
    }

    void requireSingleCapture(String challenge);

    void requireMixedSources(String challenge);

    void requireRulesOfEverySource(List<MetricDefinition> metrics, List<ScoringRule> rules, String challenge);

    /** Puntaje separado por fuente; vacío si la corrida se captura en una sola operación. */
    Optional<SourcedScore> scoreBySource(ChallengeSpec challenge, ScoringContext context);

    /** Métricas que el sistema automático mide, de entre las del desafío. */
    List<MetricDefinition> measuredAmong(List<MetricDefinition> metrics);
}
