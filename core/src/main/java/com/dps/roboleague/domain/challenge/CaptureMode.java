package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.scoring.ScoringRule;
import java.util.List;

/** Cómo llegan los datos de una corrida: en una sola captura, o de dos fuentes (automática y jurado). */
public sealed interface CaptureMode permits SingleCapture, MixedSources {

    static CaptureMode single() {
        return new SingleCapture();
    }

    void requireSingleCapture(String challenge);

    void requireMixedSources(String challenge);

    void requireRulesOfEverySource(List<MetricDefinition> metrics, List<ScoringRule> rules, String challenge);

    /** Métricas que el sistema automático mide, de entre las del desafío. */
    List<MetricDefinition> measuredAmong(List<MetricDefinition> metrics);
}
