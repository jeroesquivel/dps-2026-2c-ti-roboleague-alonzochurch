package com.dps.roboleague.adapter.in.web.event;

import com.dps.roboleague.domain.appeal.AppealWindow;
import com.dps.roboleague.domain.challenge.AttemptLimit;
import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.MetricDefinition;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricKind;
import com.dps.roboleague.domain.challenge.MetricRequirement;
import com.dps.roboleague.domain.challenge.MetricUnit;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.challenge.MixedSources;
import com.dps.roboleague.domain.competition.RobotClass;
import com.dps.roboleague.domain.eligibility.EligibilityRequirements;
import com.dps.roboleague.domain.eligibility.EligibilityRule;
import com.dps.roboleague.domain.eligibility.rule.AgeRangeRule;
import com.dps.roboleague.domain.eligibility.rule.RequiredDocumentsRule;
import com.dps.roboleague.domain.eligibility.rule.RobotClassRule;
import com.dps.roboleague.domain.eligibility.rule.RobotSpecificationRule;
import com.dps.roboleague.domain.eligibility.rule.TeamCompositionRule;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.FindCompetition;
import com.dps.roboleague.domain.port.in.PublishRulebook;
import com.dps.roboleague.domain.ranking.AttemptAggregation;
import com.dps.roboleague.domain.ranking.TiebreakRule;
import com.dps.roboleague.domain.ranking.aggregation.BestAttempt;
import com.dps.roboleague.domain.ranking.aggregation.SumOfAttempts;
import com.dps.roboleague.domain.ranking.rule.FastestMetricTiebreak;
import com.dps.roboleague.domain.ranking.rule.FewestPenaltiesTiebreak;
import com.dps.roboleague.domain.ranking.rule.HighestSingleRunTiebreak;
import com.dps.roboleague.domain.rulebook.RulebookDraft;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.scoring.BonusCap;
import com.dps.roboleague.domain.scoring.BonusPoints;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.scoring.PenaltyDefinition;
import com.dps.roboleague.domain.scoring.PointsCap;
import com.dps.roboleague.domain.scoring.PointsDeducted;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.domain.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.domain.scoring.rule.PrecisionScoringRule;
import com.dps.roboleague.domain.scoring.rule.ResourceScoringRule;
import com.dps.roboleague.domain.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.domain.scoring.rule.TimeScoringRule;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.team.Dimensions;
import com.dps.roboleague.domain.team.DocumentType;
import com.dps.roboleague.domain.team.Weight;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Traduce entre la configuración HTTP del evento y los tipos del núcleo. Verifica presencia y
 * formato (campos obligatorios, tipos de variante conocidos, números enteros donde corresponde);
 * las invariantes de cada tipo (rangos, métricas definidas, versiones) las decide el dominio al
 * construirlos, y el reglamento se arma completo antes de invocar el puerto, de modo que una
 * configuración inválida no deja nada creado.
 */
final class EventWebMapper {

    private static final String SCORING = "scoring rule";
    private static final String ELIGIBILITY = "eligibility rule";

    private EventWebMapper() {
    }

    // ---- temporada y competencia ----------------------------------------------------------------------

    static CreateSeason.Command toCommand(CreateSeasonRequest request) {
        return new CreateSeason.Command(text(request.name(), "season name"),
                required(request.year(), "season year"), periodOf(request.period(), "season period"),
                Actor.of(request.actor()));
    }

    static CreateCompetition.Command toCommand(String seasonId, CreateCompetitionRequest request) {
        List<CreateCompetition.CategoryDraft> categories = required(request.categories(), "categories").stream()
                .map(EventWebMapper::categoryOf)
                .toList();
        return new CreateCompetition.Command(SeasonId.of(seasonId), text(request.name(), "competition name"),
                periodOf(request.period(), "competition period"), categories,
                rulebookOf(request.rulebook()), Actor.of(request.actor()));
    }

    static PublishRulebook.Command toCommand(String competitionId, PublishRulebookRequest request) {
        return new PublishRulebook.Command(CompetitionId.of(competitionId), rulebookOf(request.rulebook()),
                Actor.of(request.actor()));
    }

    static SeasonCreatedResponse toResponse(SeasonId seasonId) {
        return new SeasonCreatedResponse(seasonId.value());
    }

    /** Une cada identificador devuelto con el nombre de la categoría de entrada que ocupa su posición. */
    static CompetitionCreatedResponse toResponse(CreateCompetition.Result result,
            CreateCompetitionRequest request) {
        List<CompetitionCreatedResponse.CategoryCreated> categories = new java.util.ArrayList<>();
        for (int i = 0; i < result.categoryIds().size(); i++) {
            categories.add(new CompetitionCreatedResponse.CategoryCreated(result.categoryIds().get(i).value(),
                    request.categories().get(i).name()));
        }
        return new CompetitionCreatedResponse(result.competitionId().value(),
                RulebookVersion.first().toString(), categories);
    }

    static CompetitionResponse toResponse(FindCompetition.View view) {
        return new CompetitionResponse(view.id().value(), view.name(),
                new PeriodDto(view.period().start().toString(), view.period().end().toString()),
                view.activeRulebookVersion().toString(),
                view.categories().stream()
                        .map(category -> new CompetitionResponse.CategoryResponse(category.id().value(),
                                category.name(), category.ageRange().minimumYears(),
                                category.ageRange().maximumYears(), category.robotClass().code()))
                        .toList());
    }

    static RulebookPublishedResponse toResponse(CompetitionId competitionId, RulebookVersion version) {
        return new RulebookPublishedResponse(competitionId.value(), version.toString());
    }

    private static CreateCompetition.CategoryDraft categoryOf(CreateCompetitionRequest.CategoryRequest request) {
        CreateCompetitionRequest.CategoryRequest category = required(request, "category");
        return new CreateCompetition.CategoryDraft(text(category.name(), "category name"),
                AgeRange.between(required(category.minimumAge(), "category minimum age"),
                        required(category.maximumAge(), "category maximum age")),
                RobotClass.of(required(category.robotClass(), "category robot class")));
    }

    // ---- reglamento ------------------------------------------------------------------------------------

    static RulebookDraft rulebookOf(RulebookRequest request) {
        RulebookRequest rulebook = required(request, "rulebook");
        return new RulebookDraft(
                required(rulebook.challenges(), "rulebook challenges").stream()
                        .map(EventWebMapper::challengeOf).toList(),
                new EligibilityRequirements(required(rulebook.eligibility(), "rulebook eligibility").stream()
                        .map(EventWebMapper::eligibilityRuleOf).toList()),
                aggregationOf(rulebook.attemptAggregation()),
                required(rulebook.tiebreaks(), "rulebook tiebreaks").stream()
                        .map(EventWebMapper::tiebreakOf).toList(),
                AppealWindow.of(Duration.ofMinutes(required(rulebook.appealWindowMinutes(),
                        "rulebook appeal window in minutes"))));
    }

    private static ChallengeSpec challengeOf(RulebookRequest.ChallengeRequest request) {
        RulebookRequest.ChallengeRequest challenge = required(request, "challenge");
        return new ChallengeSpec(ChallengeId.of(required(challenge.id(), "challenge id")),
                text(challenge.name(), "challenge name"),
                required(challenge.metrics(), "challenge metrics").stream().map(EventWebMapper::metricOf).toList(),
                required(challenge.scoringRules(), "challenge scoring rules").stream()
                        .map(EventWebMapper::scoringRuleOf).toList(),
                required(challenge.penalties(), "challenge penalties").stream()
                        .map(EventWebMapper::penaltyOf).toList(),
                AttemptLimit.of(required(challenge.maximumAttempts(), "challenge maximum attempts")),
                Optional.ofNullable(challenge.bestRounds()).map(EventWebMapper::bestRoundsOf),
                Optional.ofNullable(challenge.bonusCap())
                        .map(cap -> new BonusCap(pointsCap(cap.maximumPoints(), "bonus cap maximum points"))),
                Boolean.TRUE.equals(challenge.mixedSources()) ? Optional.of(new MixedSources()) : Optional.empty());
    }

    private static MetricDefinition metricOf(RulebookRequest.MetricRequest request) {
        RulebookRequest.MetricRequest metric = required(request, "metric");
        return new MetricDefinition(MetricKey.of(required(metric.key(), "metric key")),
                enumOf(MetricKind.class, metric.kind(), "metric kind"),
                MetricUnit.of(required(metric.unit(), "metric unit")),
                metric.requirement() == null ? MetricRequirement.REQUIRED
                        : enumOf(MetricRequirement.class, metric.requirement(), "metric requirement"));
    }

    private static PenaltyDefinition penaltyOf(RulebookRequest.PenaltyRequest request) {
        RulebookRequest.PenaltyRequest penalty = required(request, "penalty");
        return new PenaltyDefinition(PenaltyCode.of(required(penalty.code(), "penalty code")),
                text(penalty.description(), "penalty description"),
                PointsDeducted.of(whole(penalty.pointsDeducted(), "penalty points deducted")));
    }

    private static BestRounds bestRoundsOf(RulebookRequest.BestRoundsRequest request) {
        return BestRounds.of(required(request.counted(), "best rounds counted"),
                required(request.outOf(), "best rounds out of"));
    }

    // ---- reglas de puntaje -----------------------------------------------------------------------------

    private static ScoringRule scoringRuleOf(RulebookRequest.ScoringRuleRequest request) {
        RulebookRequest.ScoringRuleRequest rule = required(request, SCORING);
        String type = typeOf(rule.type(), SCORING);
        Function<String, String> field = name -> SCORING + " " + type + " " + name;
        return switch (type) {
            case "TIME" -> new TimeScoringRule(metricOf(rule.metric(), field.apply("metric")),
                    seconds(rule.referenceSeconds(), field.apply("referenceSeconds")),
                    rate(rule.pointsPerSecondSaved(), field.apply("pointsPerSecondSaved")),
                    pointsCap(rule.maximumPoints(), field.apply("maximumPoints")));
            case "OBJECTIVES" -> new ObjectiveScoringRule(metricOf(rule.metric(), field.apply("metric")),
                    rate(rule.pointsPerObjective(), field.apply("pointsPerObjective")),
                    required(rule.maximumObjectives(), field.apply("maximumObjectives")));
            case "PRECISION" -> new PrecisionScoringRule(metricOf(rule.metric(), field.apply("metric")),
                    pointsCap(rule.maximumPoints(), field.apply("maximumPoints")));
            case "RESOURCE" -> new ResourceScoringRule(metricOf(rule.metric(), field.apply("metric")),
                    MetricValue.of(required(rule.allowance(), field.apply("allowance"))),
                    rate(rule.pointsPerUnitOver(), field.apply("pointsPerUnitOver")));
            case "JUDGES" -> new JudgePanelScoringRule(metricOf(rule.criterion(), field.apply("criterion")),
                    rate(rule.weight(), field.apply("weight")));
            case "BONUS" -> new ThresholdBonusRule(metricOf(rule.metric(), field.apply("metric")),
                    enumOf(ThresholdBonusRule.Comparison.class, rule.comparison(), field.apply("comparison")),
                    MetricValue.of(required(rule.threshold(), field.apply("threshold"))),
                    BonusPoints.of(whole(rule.bonusPoints(), field.apply("bonusPoints"))));
            default -> throw unsupported(SCORING, type,
                    List.of("TIME", "OBJECTIVES", "PRECISION", "RESOURCE", "JUDGES", "BONUS"));
        };
    }

    // ---- elegibilidad, agregación y desempates ---------------------------------------------------------

    private static EligibilityRule eligibilityRuleOf(RulebookRequest.EligibilityRuleRequest request) {
        RulebookRequest.EligibilityRuleRequest rule = required(request, ELIGIBILITY);
        String type = typeOf(rule.type(), ELIGIBILITY);
        Function<String, String> field = name -> ELIGIBILITY + " " + type + " " + name;
        return switch (type) {
            case "AGE_RANGE" -> new AgeRangeRule();
            case "ROBOT_CLASS" -> new RobotClassRule();
            case "TEAM_COMPOSITION" -> new TeamCompositionRule(
                    required(rule.minimumCompetitors(), field.apply("minimumCompetitors")),
                    required(rule.maximumCompetitors(), field.apply("maximumCompetitors")),
                    required(rule.coachRequiredUnderAge(), field.apply("coachRequiredUnderAge")));
            case "ROBOT_SPECIFICATION" -> {
                RulebookRequest.DimensionsRequest size = required(rule.maximumDimensionsMm(),
                        field.apply("maximumDimensionsMm"));
                yield new RobotSpecificationRule(
                        Weight.ofKilograms(required(rule.maximumWeightKg(), field.apply("maximumWeightKg"))
                                .toPlainString()),
                        new Dimensions(required(size.length(), field.apply("maximumDimensionsMm.length")),
                                required(size.width(), field.apply("maximumDimensionsMm.width")),
                                required(size.height(), field.apply("maximumDimensionsMm.height"))));
            }
            case "REQUIRED_DOCUMENTS" -> {
                Set<DocumentType> documents = EnumSet.noneOf(DocumentType.class);
                required(rule.documents(), field.apply("documents")).forEach(
                        document -> documents.add(enumOf(DocumentType.class, document, field.apply("documents"))));
                yield new RequiredDocumentsRule(documents);
            }
            default -> throw unsupported(ELIGIBILITY, type, List.of("AGE_RANGE", "TEAM_COMPOSITION", "ROBOT_CLASS",
                    "ROBOT_SPECIFICATION", "REQUIRED_DOCUMENTS"));
        };
    }

    private static AttemptAggregation aggregationOf(RulebookRequest.TypeRequest request) {
        String type = typeOf(required(request, "attempt aggregation").type(), "attempt aggregation");
        return switch (type) {
            case BestAttempt.CODE -> new BestAttempt();
            case SumOfAttempts.CODE -> new SumOfAttempts();
            default -> throw unsupported("attempt aggregation", type,
                    List.of(BestAttempt.CODE, SumOfAttempts.CODE));
        };
    }

    private static TiebreakRule tiebreakOf(RulebookRequest.TiebreakRequest request) {
        RulebookRequest.TiebreakRequest tiebreak = required(request, "tiebreak");
        String type = typeOf(tiebreak.type(), "tiebreak");
        return switch (type) {
            case HighestSingleRunTiebreak.CODE -> new HighestSingleRunTiebreak();
            case FewestPenaltiesTiebreak.CODE -> new FewestPenaltiesTiebreak();
            case FastestMetricTiebreak.CODE -> new FastestMetricTiebreak(
                    metricOf(tiebreak.metric(), "tiebreak " + type + " metric"));
            default -> throw unsupported("tiebreak", type, List.of(HighestSingleRunTiebreak.CODE,
                    FewestPenaltiesTiebreak.CODE, FastestMetricTiebreak.CODE));
        };
    }

    // ---- valores ---------------------------------------------------------------------------------------

    private static MetricKey metricOf(String key, String field) {
        return MetricKey.of(required(key, field));
    }

    private static PointsRate rate(BigDecimal value, String field) {
        return PointsRate.of(required(value, field).toPlainString());
    }

    private static PointsCap pointsCap(BigDecimal value, String field) {
        return PointsCap.of(required(value, field).toPlainString());
    }

    private static Duration seconds(BigDecimal value, String field) {
        try {
            return Duration.ofMillis(required(value, field).movePointRight(3).longValueExact());
        } catch (ArithmeticException exception) {
            throw new InvalidValueException(field + " must have at most three decimals and fit a duration");
        }
    }

    /** Un entero exacto: {@code 3} y {@code 3.0} se aceptan, {@code 3.5} no se trunca en silencio. */
    private static long whole(BigDecimal value, String field) {
        try {
            return required(value, field).longValueExact();
        } catch (ArithmeticException exception) {
            throw new InvalidValueException(field + " must be a whole number and was " + value);
        }
    }

    private static DateRange periodOf(PeriodDto period, String field) {
        PeriodDto required = required(period, field);
        return DateRange.of(dateOf(required.start(), field + " start"), dateOf(required.end(), field + " end"));
    }

    private static LocalDate dateOf(String value, String field) {
        try {
            return LocalDate.parse(required(value, field));
        } catch (DateTimeParseException exception) {
            throw new InvalidValueException(field + " " + value + " is not an ISO date (yyyy-MM-dd)");
        }
    }

    private static String typeOf(String type, String family) {
        return required(type, family + " type").trim().toUpperCase(Locale.ROOT);
    }

    private static InvalidValueException unsupported(String family, String type, List<String> supported) {
        return new InvalidValueException(family + " type " + type + " is not supported; use one of " + supported);
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value, String field) {
        String text = required(value, field).trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw new InvalidValueException(field + " must be one of " + Arrays.toString(type.getEnumConstants())
                    + " and was " + value);
        }
    }

    private static String text(String value, String field) {
        return required(value, field);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new InvalidValueException(field + " is required");
        }
        return value;
    }
}
