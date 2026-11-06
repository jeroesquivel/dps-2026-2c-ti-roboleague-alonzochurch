package com.dps.roboleague.application.usecase;

import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.results.port.in.CaptureRunResult;
import com.dps.roboleague.rules.challenge.AttemptLimit;
import com.dps.roboleague.rules.challenge.AttemptNumber;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.challenge.MetricDefinition;
import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricKind;
import com.dps.roboleague.rules.challenge.MetricUnit;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.port.in.PublishRulebook;
import com.dps.roboleague.rules.rulebook.RulebookDraft;
import com.dps.roboleague.rules.scoring.ContributionKind;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.PointsCap;
import com.dps.roboleague.rules.scoring.PointsRate;
import com.dps.roboleague.rules.scoring.ScoreContribution;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.rules.scoring.ScoringRule;
import com.dps.roboleague.rules.scoring.ScoringRuleCode;
import com.dps.roboleague.rules.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.rules.scoring.rule.PrecisionScoringRule;
import com.dps.roboleague.rules.scoring.rule.TimeScoringRule;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.Identifier;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RuleViolationException;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.port.in.FindAuditTrail;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TestEdition;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulebookEvolutionTest {

    private static final MetricKey PRECISION = MetricKey.of("PRECISION");

    private final TestEdition edition = TestEdition.start();
    private final TeamId delta = edition.registerEligibleTeam("Delta Bots");
    private final RoundId firstRound = edition.scheduleRoundFor(1, List.of(delta));

    @Test
    void successivePublicationsKeepTheOriginalScoreAndRecordEachVersion() {
        RunId originalRun = capture(firstRound);
        CalculateRunScore.RunScore originalScore = score(originalRun);
        MeasurementSet originalMeasurements = edition.runResult(originalRun).originalMeasurements();

        RulebookVersion second = edition.publishRulebookWith(
                new ObjectiveScoringRule(RescueEditionFixture.OBJECTIVES, PointsRate.of(100), 5));
        RunId runUnderSecondRulebook = capture(scheduleSecondRound());
        RulebookVersion third = edition.publishRulebookWith(
                new ObjectiveScoringRule(RescueEditionFixture.OBJECTIVES, PointsRate.of(200), 5));

        assertEquals(RulebookVersion.of(2), second);
        assertEquals(RulebookVersion.of(3), third);
        assertEquals(third, activeVersion());
        assertEquals(List.of("v1", "v2", "v3"), publicationEvents().stream()
                .map(event -> event.details().get(AuditDetail.VERSION)).toList());
        assertEquals(RulebookVersion.first(), edition.round(firstRound).rulebookVersion());
        assertEquals(RulebookVersion.first(), score(originalRun).rulebookVersion());
        assertEquals(Points.of("60.75"), score(originalRun).total());
        assertEquals(RulebookVersion.of(2), score(runUnderSecondRulebook).rulebookVersion());
        assertEquals(Points.of(400), score(runUnderSecondRulebook).total());
        assertEquals(originalScore.breakdown(), score(originalRun).breakdown());
        assertEquals(originalMeasurements, edition.runResult(originalRun).originalMeasurements());
        assertTrue(edition.runResult(originalRun).corrections().isEmpty());
    }

    @Test
    void aRoundScheduledUnderVersionOneCanBeCapturedAfterVersionTwoAddsARequiredMetric() {
        publish(edition.competitionId(), List.of(challengeRequiringPrecision()));

        RunId lateCapture = capture(firstRound);

        assertEquals(RulebookVersion.of(2), activeVersion());
        assertEquals(RulebookVersion.first(), edition.runResult(lateCapture).rulebookVersion());
        assertTrue(edition.runResult(lateCapture).originalMeasurements().find(PRECISION).isEmpty());
        assertEquals(Points.of("60.75"), score(lateCapture).total());
        List<AuditEvent> captureEvents = auditFor(lateCapture);
        assertEquals(1, captureEvents.size());
        assertEquals(AuditAction.RESULT_CAPTURED, captureEvents.getFirst().action());
        assertEquals("v1", captureEvents.getFirst().details().get(AuditDetail.RULEBOOK));
    }

    @Test
    void newRoundsRequireTheNewMetricAndUseTheNewFormulaWithoutChangingEarlierRuns() {
        RunId originalRun = capture(firstRound);
        publish(edition.competitionId(), List.of(challengeRequiringPrecision()));
        RoundId secondRound = scheduleSecondRound();

        RuleViolationException missingMetric = assertThrows(RuleViolationException.class, () -> capture(secondRound));
        assertTrue(missingMetric.getMessage().contains(PRECISION.value()));

        MeasurementSet complete = edition.measurements("95.5", 4, "42")
                .with(PRECISION, MetricValue.of("0.75"));
        RunId newRun = edition.module().captureRunResultUseCase().execute(
                new CaptureRunResult.Command(secondRound, delta, AttemptNumber.first(), complete,
                        edition.evaluations(List.of(8, 9)), List.of(), TestEdition.ACTOR));

        assertEquals(RulebookVersion.of(2), edition.round(secondRound).rulebookVersion());
        assertEquals(RulebookVersion.of(2), edition.runResult(newRun).rulebookVersion());
        assertEquals(Points.of("15.00"), score(newRun).total());
        assertEquals(Points.of("15.00"), score(newRun).breakdown().totalFor(PrecisionScoringRule.CODE));
        assertEquals(Points.of("60.75"), score(originalRun).total());
        assertEquals(List.of(AuditAction.RESULT_CAPTURED), edition.auditActionsFor(newRun));
    }

    @Test
    void aNewScoringRuleCanBeConfiguredAlongsideExistingRulesAndThePenaltyCatalog() {
        List<ScoringRule> extended = List.of(
                new SquaredObjectivesRule(RescueEditionFixture.OBJECTIVES, PointsRate.of("2.50")),
                new TimeScoringRule(RescueEditionFixture.TIME, Duration.ofSeconds(120), PointsRate.of("0.50"),
                        PointsCap.of(30)));
        publish(edition.competitionId(), List.of(RescueEditionFixture.challengeScoredBy(extended)));
        RoundId secondRound = scheduleSecondRound();

        RunId run = edition.capture(secondRound, delta, "95.5", 4, "42", List.of(8, 9),
                List.of(IncidentReport.once(RescueEditionFixture.RESTART)));
        CalculateRunScore.RunScore result = score(run);

        assertEquals(RulebookVersion.of(2), result.rulebookVersion());
        assertEquals(Points.of("40.00"), result.breakdown().totalFor(SquaredObjectivesRule.CODE));
        assertEquals(Points.of("12.25"), result.breakdown().totalFor(TimeScoringRule.CODE));
        assertEquals(Points.of("-3.00"), result.breakdown().totalOf(ContributionKind.PENALTY));
        assertEquals(Points.of("49.25"), result.total());
        assertEquals(3, result.breakdown().contributions().size());
        assertTrue(result.breakdown().contributions().stream()
                .anyMatch(contribution -> contribution.ruleCode().equals(SquaredObjectivesRule.CODE)
                        && contribution.explanation().contains("4.000 squared")));
    }

    @Test
    void rejectingAnEmptyRulebookLeavesTheCurrentVersionAndItsPublicationHistoryUntouched() {
        RunId originalRun = capture(firstRound);
        List<AuditEvent> historyBefore = publicationEvents();

        assertThrows(InvalidValueException.class, () -> publish(edition.competitionId(), List.of()));

        assertEquals(RulebookVersion.first(), activeVersion());
        assertEquals(historyBefore, publicationEvents());
        assertEquals(Points.of("60.75"), score(originalRun).total());
        RulebookVersion nextPublished = edition.publishRulebookWith(
                new ObjectiveScoringRule(RescueEditionFixture.OBJECTIVES, PointsRate.of(100), 5));
        assertEquals(RulebookVersion.of(2), nextPublished);
        assertEquals(List.of("v1", "v2"), publicationEvents().stream()
                .map(event -> event.details().get(AuditDetail.VERSION)).toList());
    }

    @Test
    void publishingForAnUnknownCompetitionDoesNotChangeAnExistingEditionOrCreateAnAuditEntry() {
        CompetitionId unknown = CompetitionId.of("MISSING-COMPETITION");
        List<AuditEvent> historyBefore = publicationEvents();

        assertThrows(NotFoundException.class,
                () -> publish(unknown, List.of(RescueEditionFixture.rescueChallenge())));

        assertEquals(RulebookVersion.first(), activeVersion());
        assertEquals(historyBefore, publicationEvents());
        assertTrue(auditFor(unknown).isEmpty());
        assertEquals(Points.of("60.75"), score(capture(firstRound)).total());
        assertEquals(RulebookVersion.of(2), edition.publishRulebookWith(
                new ObjectiveScoringRule(RescueEditionFixture.OBJECTIVES, PointsRate.of(100), 5)));
    }

    private ChallengeSpec challengeRequiringPrecision() {
        List<MetricDefinition> metrics = new ArrayList<>(RescueEditionFixture.metrics());
        metrics.add(MetricDefinition.required(PRECISION, MetricKind.PRECISION_RATIO, MetricUnit.of("ratio")));
        return ChallengeSpec.builder(RescueEditionFixture.CHALLENGE_ID, "Rescue with precision")
                .metrics(metrics)
                .scoringRules(List.of(new PrecisionScoringRule(PRECISION, PointsCap.of(20))))
                .penalties(RescueEditionFixture.penalties())
                .maximumAttempts(AttemptLimit.of(2))
                .build();
    }

    private RulebookVersion publish(CompetitionId competitionId, List<ChallengeSpec> challenges) {
        return edition.module().publishRulebookUseCase().execute(new PublishRulebook.Command(competitionId,
                new RulebookDraft(challenges, RescueEditionFixture.eligibilityRequirements(),
                        RescueEditionFixture.attemptAggregation(), RescueEditionFixture.tiebreaks(),
                        RescueEditionFixture.appealWindow()),
                TestEdition.ACTOR));
    }

    private RulebookVersion activeVersion() {
        return edition.module().findCompetitionUseCase()
                .execute(edition.competitionId()).activeRulebookVersion();
    }

    private List<AuditEvent> publicationEvents() {
        return auditFor(edition.competitionId()).stream()
                .filter(event -> event.action() == AuditAction.RULEBOOK_PUBLISHED).toList();
    }

    private List<AuditEvent> auditFor(Identifier subject) {
        return edition.module().findAuditTrailUseCase().execute(new FindAuditTrail.Command(subject));
    }

    private RoundId scheduleSecondRound() {
        return edition.scheduleRound(2,
                List.of(edition.heat(delta, "A2", LocalDateTime.of(2026, 3, 2, 12, 0))));
    }

    private RunId capture(RoundId roundId) {
        return edition.capture(roundId, delta, "95.5", 4, "42", List.of(8, 9), List.of());
    }

    private CalculateRunScore.RunScore score(RunId runId) {
        return edition.module().calculateRunScoreUseCase().execute(new CalculateRunScore.Command(runId));
    }

    private record SquaredObjectivesRule(MetricKey metric, PointsRate multiplier) implements ScoringRule {

        private static final ScoringRuleCode CODE = ScoringRuleCode.of("SQUARED_OBJECTIVES");

        @Override
        public List<ScoreContribution> apply(ScoringContext context) {
            return context.measurements().amountOf(metric)
                    .map(amount -> List.of(ScoreContribution.earned(CODE,
                            amount.toPlainString() + " squared objectives at " + multiplier + " points",
                            multiplier.times(amount.multiply(amount)))))
                    .orElseGet(() -> List.of(ScoreContribution.earned(CODE,
                            "no measurement recorded for " + metric.value(), Points.ZERO)));
        }

        @Override
        public Set<MetricKey> referencedMetrics() {
            return Set.of(metric);
        }
    }
}
