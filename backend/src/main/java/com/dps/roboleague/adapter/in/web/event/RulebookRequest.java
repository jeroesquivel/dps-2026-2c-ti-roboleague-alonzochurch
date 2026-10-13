package com.dps.roboleague.adapter.in.web.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Representación HTTP de un reglamento. Las familias polimórficas (reglas de puntaje, de
 * elegibilidad y de desempate, y la agregación de intentos) se expresan con un discriminador
 * {@code type} y los parámetros de esa variante en el mismo objeto; los parámetros que no
 * corresponden a la variante se ignoran y los que faltan se informan como error de entrada.
 *
 * <p>Los valores de {@code type} son los códigos que ya usa el dominio y la auditoría: puntaje
 * {@code TIME}, {@code OBJECTIVES}, {@code PRECISION}, {@code RESOURCE}, {@code JUDGES},
 * {@code BONUS}; elegibilidad {@code AGE_RANGE}, {@code TEAM_COMPOSITION}, {@code ROBOT_CLASS},
 * {@code ROBOT_SPECIFICATION}, {@code REQUIRED_DOCUMENTS}; agregación {@code BEST_ATTEMPT},
 * {@code SUM_OF_ATTEMPTS}; desempate {@code HIGHEST_SINGLE_RUN}, {@code FEWEST_PENALTIES},
 * {@code FASTEST_METRIC}.
 */
public record RulebookRequest(List<ChallengeRequest> challenges, List<EligibilityRuleRequest> eligibility,
        TypeRequest attemptAggregation, List<TiebreakRequest> tiebreaks, Integer appealWindowMinutes) {

    public record ChallengeRequest(String id, String name, List<MetricRequest> metrics,
            List<ScoringRuleRequest> scoringRules, List<PenaltyRequest> penalties, Integer maximumAttempts,
            BestRoundsRequest bestRounds, BonusCapRequest bonusCap, Boolean mixedSources) {
    }

    public record MetricRequest(String key, String kind, String unit, String requirement) {
    }

    public record PenaltyRequest(String code, String description, BigDecimal pointsDeducted) {
    }

    public record BestRoundsRequest(Integer counted, Integer outOf) {
    }

    public record BonusCapRequest(BigDecimal maximumPoints) {
    }

    /** Una regla de puntaje; cada variante usa sólo los campos que le corresponden. */
    public record ScoringRuleRequest(String type, String metric, String criterion, BigDecimal referenceSeconds,
            BigDecimal pointsPerSecondSaved, BigDecimal maximumPoints, BigDecimal pointsPerObjective,
            Integer maximumObjectives, BigDecimal allowance, BigDecimal pointsPerUnitOver, BigDecimal weight,
            String comparison, BigDecimal threshold, BigDecimal bonusPoints) {
    }

    public record EligibilityRuleRequest(String type, Integer minimumCompetitors, Integer maximumCompetitors,
            Integer coachRequiredUnderAge, BigDecimal maximumWeightKg, DimensionsRequest maximumDimensionsMm,
            List<String> documents) {
    }

    public record DimensionsRequest(Integer length, Integer width, Integer height) {
    }

    public record TiebreakRequest(String type, String metric) {
    }

    public record TypeRequest(String type) {
    }
}
