package com.dps.roboleague.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.appeal.AppealDecision;
import com.dps.roboleague.domain.challenge.AttemptLimit;
import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricDefinition;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricKind;
import com.dps.roboleague.domain.challenge.MetricUnit;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.competition.Category;
import com.dps.roboleague.domain.competition.Competition;
import com.dps.roboleague.domain.competition.RobotClass;
import com.dps.roboleague.domain.competition.Season;
import com.dps.roboleague.domain.eligibility.EligibilityRequest;
import com.dps.roboleague.domain.eligibility.EligibilityRuleCode;
import com.dps.roboleague.domain.eligibility.EligibilityVerdict;
import com.dps.roboleague.domain.eligibility.EligibilityViolation;
import com.dps.roboleague.domain.eligibility.rule.RobotSpecificationRule;
import com.dps.roboleague.domain.eligibility.rule.TeamCompositionRule;
import com.dps.roboleague.domain.ranking.AppliedTiebreak;
import com.dps.roboleague.domain.ranking.Revision;
import com.dps.roboleague.domain.ranking.StandingEntry;
import com.dps.roboleague.domain.result.ResultCorrection;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.Heat;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.ScheduleConflict;
import com.dps.roboleague.domain.schedule.ScheduleConflictDetector;
import com.dps.roboleague.domain.schedule.ScheduleConflictType;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.scoring.ContributionKind;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.JudgeScore;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.scoring.PenaltyDefinition;
import com.dps.roboleague.domain.scoring.PointsAmount;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.scoring.ScoringRuleCode;
import com.dps.roboleague.domain.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.domain.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AgeRange;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ArenaId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.HeatId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.MemberId;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.domain.team.Dimensions;
import com.dps.roboleague.domain.team.DocumentType;
import com.dps.roboleague.domain.team.Member;
import com.dps.roboleague.domain.team.MemberRole;
import com.dps.roboleague.domain.team.Robot;
import com.dps.roboleague.domain.team.TeamDocument;
import com.dps.roboleague.domain.team.TeamMembers;
import com.dps.roboleague.domain.team.TeamRegistration;
import com.dps.roboleague.domain.team.Weight;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DomainEdgeCasesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 2);
    private static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");
    private static final Category CATEGORY = new Category(CategoryId.of("CAT-1"), "Junior",
            AgeRange.between(12, 17), TeamFixtures.RESCUE_BOT);

    @Test
    void rejectsInvalidCodesAndTextValues() {
        assertThrows(InvalidValueException.class, () -> TeamId.of(null));
        assertThrows(InvalidValueException.class, () -> TeamId.of(" "));
        assertThrows(InvalidValueException.class, () -> MemberId.of(" "));
        assertThrows(InvalidValueException.class, () -> PenaltyCode.of(null));
        assertThrows(InvalidValueException.class, () -> PenaltyCode.of(" "));
        assertThrows(InvalidValueException.class, () -> ScoringRuleCode.of(null));
        assertThrows(InvalidValueException.class, () -> ScoringRuleCode.of(" "));
        assertThrows(InvalidValueException.class, () -> EligibilityRuleCode.of(null));
        assertThrows(InvalidValueException.class, () -> EligibilityRuleCode.of(" "));
        assertThrows(InvalidValueException.class, () -> MetricKey.of(null));
        assertThrows(InvalidValueException.class, () -> MetricKey.of(" "));
        assertThrows(InvalidValueException.class, () -> MetricUnit.of(null));
        assertThrows(InvalidValueException.class, () -> MetricUnit.of(" "));
        assertThrows(InvalidValueException.class, () -> RobotClass.of(null));
        assertThrows(InvalidValueException.class, () -> RobotClass.of(" "));
        assertThrows(InvalidValueException.class, () -> Actor.of(null));
        assertThrows(InvalidValueException.class, () -> Actor.of(" "));
        EligibilityRuleCode rule = EligibilityRuleCode.of("rule");
        assertThrows(InvalidValueException.class, () -> new EligibilityViolation(rule, null));
        assertThrows(InvalidValueException.class, () -> new EligibilityViolation(rule, " "));
        assertEquals("RULE: reason", new EligibilityViolation(rule, "reason").toString());
        assertThrows(InvalidValueException.class, () -> new AppliedTiebreak(null, "description"));
        assertThrows(InvalidValueException.class, () -> new AppliedTiebreak(" ", "description"));
        assertThrows(InvalidValueException.class, () -> new AppliedTiebreak("RULE", null));
        assertThrows(InvalidValueException.class, () -> new AppliedTiebreak("RULE", " "));
        Actor reviewer = Actor.of(" head-judge ");
        assertEquals("head-judge", reviewer.toString());
        assertThrows(InvalidValueException.class, () -> new AppealDecision(reviewer, null, NOW));
        assertThrows(InvalidValueException.class, () -> new AppealDecision(reviewer, " ", NOW));
    }

    @Test
    void rejectsInvalidRangesAndNumericValues() {
        assertThrows(InvalidValueException.class, () -> DateRange.of(TODAY, TODAY.minusDays(1)));
        assertThrows(InvalidValueException.class, () -> AgeRange.between(-1, 17));
        assertThrows(InvalidValueException.class, () -> AgeRange.between(18, 17));
        assertFalse(AgeRange.between(12, 17).includes(18));
        assertEquals(Points.of(5), Points.of(5).cappedAt(Points.of(10)));
        assertThrows(InvalidValueException.class, () -> RulebookVersion.of(0));
        assertFalse(RulebookVersion.first().isNewerThan(RulebookVersion.of(2)));
        assertThrows(InvalidValueException.class, () -> MetricValue.of("-0.1"));
        assertEquals(MetricValue.of("1.250"), MetricValue.of(new BigDecimal("1.25")));
        assertEquals(MetricValue.of("1.250"), MetricValue.ofSeconds(Duration.ofMillis(1250)));
        assertFalse(MetricKind.TIME_SECONDS.accepts(new BigDecimal("-1")));
        assertFalse(MetricKind.PRECISION_RATIO.accepts(new BigDecimal("1.1")));
        assertTrue(MetricKind.PRECISION_RATIO.accepts(BigDecimal.ONE));
        assertFalse(MetricKind.OBJECTIVE_COUNT.accepts(new BigDecimal("1.5")));
        assertTrue(MetricKind.OBJECTIVE_COUNT.accepts(new BigDecimal("2")));
        assertTrue(MetricKind.RESOURCE_UNITS.accepts(BigDecimal.ONE));
    }

    @Test
    void countsAndOrdinalsAreValueObjectsThatRejectNonPositiveValues() {
        assertThrows(InvalidValueException.class, () -> AttemptNumber.of(0));
        assertThrows(InvalidValueException.class, () -> AttemptLimit.of(0));
        assertThrows(InvalidValueException.class, () -> RoundOrdinal.of(0));
        assertThrows(InvalidValueException.class, () -> Revision.of(0));
        assertEquals(AttemptNumber.of(1), AttemptNumber.first());
        assertTrue(AttemptLimit.of(2).allows(AttemptNumber.of(2)));
        assertFalse(AttemptLimit.of(2).allows(AttemptNumber.of(3)));
        assertEquals("2", AttemptLimit.of(2).toString());
        assertTrue(RoundOrdinal.of(3).compareTo(RoundOrdinal.of(2)) > 0);
        assertTrue(AttemptNumber.of(1).compareTo(AttemptNumber.of(2)) < 0);
        assertEquals(Revision.of(2), Revision.first().next());
        assertTrue(Revision.of(2).compareTo(Revision.first()) > 0);
    }

    @Test
    void scoringConfigurationRejectsNegativeAmountsAndJudgeScoresOutsideTheirScale() {
        assertThrows(InvalidValueException.class, () -> PointsRate.of("-0.5"));
        assertThrows(InvalidValueException.class, () -> PointsAmount.of(-1));
        assertThrows(InvalidValueException.class, () -> JudgeScore.of(-1));
        assertThrows(InvalidValueException.class, () -> new JudgeScore(new BigDecimal("10.01")));
        assertEquals(new BigDecimal("10.00"), JudgeScore.of(10).value());
        assertEquals(Points.of("2.50"), PointsRate.of("0.5").times(new BigDecimal("5")));
        assertEquals("0.5", PointsRate.of("0.5").toString());
        assertEquals("3.00", PointsAmount.of("3").toString());
    }

    @Test
    void validatesScoringValueObjectsAndAlternativeBonusComparison() {
        PenaltyCode penalty = PenaltyCode.of("RESTART");
        ScoringRuleCode scoringRule = ScoringRuleCode.of("RULE");
        MetricKey objectives = MetricKey.of("OBJECTIVES");

        assertThrows(InvalidValueException.class, () -> new PenaltyDefinition(penalty, null, PointsAmount.of(1)));
        assertThrows(InvalidValueException.class, () -> new PenaltyDefinition(penalty, " ", PointsAmount.of(1)));
        assertThrows(InvalidValueException.class,
                () -> new ScoreContribution(scoringRule, ContributionKind.EARNED, null, Points.ZERO));
        assertThrows(InvalidValueException.class,
                () -> new ScoreContribution(scoringRule, ContributionKind.EARNED, " ", Points.ZERO));
        assertThrows(InvalidValueException.class, () -> new IncidentReport(penalty, 0));
        assertThrows(InvalidValueException.class,
                () -> new ObjectiveScoringRule(objectives, PointsRate.of(1), 0));

        ThresholdBonusRule atMost = new ThresholdBonusRule(objectives, ThresholdBonusRule.Comparison.AT_MOST,
                MetricValue.of(5), PointsAmount.of(10));
        assertEquals(Points.of(10), atMost.breakdownFor(measured(objectives, "4")).total());
        assertEquals(Points.ZERO, atMost.breakdownFor(measured(objectives, "6")).total());
    }

    @Test
    void validatesTeamAndMemberState() {
        assertThrows(InvalidValueException.class, () -> new Dimensions(0, 1, 1));
        assertThrows(InvalidValueException.class, () -> new Dimensions(1, 0, 1));
        assertThrows(InvalidValueException.class, () -> new Dimensions(1, 1, 0));
        Dimensions limit = new Dimensions(10, 10, 10);
        assertFalse(new Dimensions(11, 1, 1).fitsWithin(limit));
        assertFalse(new Dimensions(1, 11, 1).fitsWithin(limit));
        assertFalse(new Dimensions(1, 1, 11).fitsWithin(limit));

        Weight weight = Weight.ofKilograms("1");
        assertThrows(InvalidValueException.class, () -> new Robot(null, TeamFixtures.RESCUE_BOT, weight, limit));
        assertThrows(InvalidValueException.class, () -> new Robot(" ", TeamFixtures.RESCUE_BOT, weight, limit));
        assertThrows(InvalidValueException.class, () -> Weight.ofKilograms("0"));
        assertEquals("2.4 kg", Weight.ofKilograms("2.400").toString());
        assertThrows(InvalidValueException.class,
                () -> TeamFixtures.member("M-1", null, TODAY.minusYears(14), MemberRole.COMPETITOR));
        assertThrows(InvalidValueException.class,
                () -> TeamFixtures.member("M-1", " ", TODAY.minusYears(14), MemberRole.COMPETITOR));
        Member futureMember = TeamFixtures.member("M-1", "Future", TODAY.plusDays(1), MemberRole.COMPETITOR);
        assertThrows(InvalidValueException.class, () -> futureMember.ageOn(TODAY));
        assertFalse(TeamFixtures.member("M-2", "Coach", TODAY.minusYears(30), MemberRole.COACH).isCompetitor());
        assertThrows(InvalidValueException.class, () -> new TeamDocument(DocumentType.TECHNICAL_SHEET, null));
        assertThrows(InvalidValueException.class, () -> new TeamDocument(DocumentType.TECHNICAL_SHEET, " "));

        assertThrows(InvalidValueException.class, () -> registration(null, TeamFixtures.eligibleMembers()));
        assertThrows(InvalidValueException.class, () -> registration(" ", TeamFixtures.eligibleMembers()));
        assertThrows(InvalidValueException.class, () -> new TeamMembers(List.of()));
        TeamRegistration registration = registration("Team", TeamFixtures.eligibleMembers());
        assertEquals(CompetitionId.of("COMP-1"), registration.competitionId());
        registration.resolveWith(new EligibilityVerdict(List.of()));
        assertThrows(ConflictException.class, () -> registration.resolveWith(new EligibilityVerdict(List.of())));
    }

    @Test
    void membersHaveTheirOwnIdentityAndCannotBeListedTwice() {
        LocalDate birthDate = TODAY.minusYears(14);
        Member first = TeamFixtures.member("M-1", "Ada Lovelace", birthDate, MemberRole.COMPETITOR);
        Member namesake = TeamFixtures.member("M-2", "Ada Lovelace", birthDate, MemberRole.COMPETITOR);

        assertFalse(first.equals(namesake));
        assertEquals(2, TeamMembers.of(first, namesake).competitors().size());
        assertThrows(InvalidValueException.class, () -> TeamMembers.of(first, first));
    }

    @Test
    void validatesTeamCompositionAndRobotDimensions() {
        TeamCompositionRule composition = new TeamCompositionRule(2, 4, 18);
        assertThrows(InvalidValueException.class, () -> new TeamCompositionRule(0, 4, 18));
        assertThrows(InvalidValueException.class, () -> new TeamCompositionRule(3, 2, 18));

        List<Member> adults = IntStream.range(0, 5)
                .mapToObj(index -> TeamFixtures.member("M-" + index, "Adult " + index, TODAY.minusYears(25),
                        MemberRole.COMPETITOR))
                .toList();
        assertEquals(1, composition.evaluate(request(registration("Large", new TeamMembers(adults)))).size());
        assertTrue(composition.evaluate(request(registration("Adults",
                new TeamMembers(adults.subList(0, 2))))).isEmpty());
        assertEquals(1, composition.evaluate(request(registration("Coach only",
                TeamMembers.of(TeamFixtures.member("M-9", "Coach", TODAY.minusYears(25), MemberRole.COACH)))))
                .size());

        Robot oversized = new Robot("Large", TeamFixtures.RESCUE_BOT, Weight.ofKilograms("1"),
                new Dimensions(201, 200, 200));
        RobotSpecificationRule specification = new RobotSpecificationRule(Weight.ofKilograms("3"),
                new Dimensions(200, 200, 200));
        assertEquals(1, specification.evaluate(request(registration("Large robot",
                TeamFixtures.eligibleMembers(), oversized))).size());
    }

    @Test
    void validatesChallengeAndRulebookBoundaries() {
        MetricKey objectives = MetricKey.of("OBJECTIVES");
        MetricDefinition definition = MetricDefinition.required(objectives, MetricKind.OBJECTIVE_COUNT,
                MetricUnit.of("count"));
        ObjectiveScoringRule scoringRule = new ObjectiveScoringRule(objectives, PointsRate.of(10), 5);

        assertThrows(InvalidValueException.class,
                () -> new ChallengeSpec(ChallengeId.of("C"), null, List.of(definition), List.of(scoringRule),
                        List.of(), AttemptLimit.of(1)));
        assertThrows(InvalidValueException.class,
                () -> new ChallengeSpec(ChallengeId.of("C"), " ", List.of(definition), List.of(scoringRule),
                        List.of(), AttemptLimit.of(1)));
        assertThrows(InvalidValueException.class,
                () -> new ChallengeSpec(ChallengeId.of("C"), "Challenge", List.of(), List.of(scoringRule),
                        List.of(), AttemptLimit.of(1)));
        assertThrows(InvalidValueException.class,
                () -> new ChallengeSpec(ChallengeId.of("C"), "Challenge", List.of(definition), List.of(),
                        List.of(), AttemptLimit.of(1)));

        ChallengeSpec challenge = new ChallengeSpec(ChallengeId.of("C"), "Challenge", List.of(definition),
                List.of(scoringRule), List.of(), AttemptLimit.of(2));
        assertThrows(RuleViolationException.class, () -> challenge.requireAttemptWithinLimit(AttemptNumber.of(3)));
        Rulebook rulebook = Rulebook.of(CompetitionId.of("COMP-1"), RulebookVersion.first(), TODAY,
                List.of(challenge), RescueEditionFixture.eligibilityPolicy(), RescueEditionFixture.attemptAggregation(),
                RescueEditionFixture.tiebreaks());
        assertThrows(NotFoundException.class, () -> rulebook.challenge(ChallengeId.of("UNKNOWN")));
        assertThrows(InvalidValueException.class, () -> Rulebook.of(CompetitionId.of("COMP-1"),
                RulebookVersion.first(), TODAY, List.of(challenge, challenge), RescueEditionFixture.eligibilityPolicy(),
                RescueEditionFixture.attemptAggregation(), RescueEditionFixture.tiebreaks()));
    }

    @Test
    void validatesCompetitionAndRankingState() {
        assertThrows(InvalidValueException.class, () -> new Category(CategoryId.of("CAT"), null,
                AgeRange.between(12, 17), TeamFixtures.RESCUE_BOT));
        assertThrows(InvalidValueException.class, () -> new Category(CategoryId.of("CAT"), " ",
                AgeRange.between(12, 17), TeamFixtures.RESCUE_BOT));
        DateRange year = DateRange.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThrows(InvalidValueException.class, () -> new Season(SeasonId.of("S"), null, 2026, year));
        assertThrows(InvalidValueException.class, () -> new Season(SeasonId.of("S"), " ", 2026, year));
        assertThrows(InvalidValueException.class, () -> new Season(SeasonId.of("S"), "Season", 2025, year));

        assertThrows(InvalidValueException.class, () -> competition(null, List.of(CATEGORY)));
        assertThrows(InvalidValueException.class, () -> competition(" ", List.of(CATEGORY)));
        assertThrows(InvalidValueException.class, () -> competition("Competition", List.of(CATEGORY, CATEGORY)));
        Competition competition = competition("Competition", List.of(CATEGORY));
        assertThrows(NotFoundException.class, () -> competition.category(CategoryId.of("UNKNOWN")));
        assertThrows(ConflictException.class, competition::requireActiveRulebookVersion);
        competition.activateRulebook(RulebookVersion.of(2));
        assertThrows(ConflictException.class, () -> competition.activateRulebook(RulebookVersion.first()));
        assertEquals(SeasonId.of("SEASON-1"), competition.seasonId());

        assertThrows(InvalidValueException.class,
                () -> new StandingEntry(0, TeamId.of("T"), Points.ZERO, List.of()));
    }

    @Test
    void validatesScheduleEntitiesAndConflictIdentity() {
        assertThrows(InvalidValueException.class,
                () -> new ScheduleConflict(ScheduleConflictType.ARENA_BUSY, null));
        assertThrows(InvalidValueException.class,
                () -> new ScheduleConflict(ScheduleConflictType.ARENA_BUSY, " "));
        assertThrows(InvalidValueException.class,
                () -> new TimeSlot(LocalDateTime.of(2026, 3, 2, 10, 0), Duration.ZERO));
        assertThrows(InvalidValueException.class,
                () -> new TimeSlot(LocalDateTime.of(2026, 3, 2, 10, 0), Duration.ofMinutes(-1)));
        TimeSlot early = new TimeSlot(LocalDateTime.of(2026, 3, 2, 9, 0), Duration.ofHours(1));
        TimeSlot later = new TimeSlot(LocalDateTime.of(2026, 3, 2, 10, 0), Duration.ofHours(1));
        assertFalse(early.overlaps(later));
        assertFalse(later.overlaps(early));

        Round round = round();
        Heat wrongRound = heat("H1", RoundId.of("OTHER"), TeamId.of("TEAM-1"), Set.of(JudgeId.of("J1")));
        assertThrows(RuleViolationException.class, () -> round.schedule(wrongRound));
        Heat first = heat("H1", round.id(), TeamId.of("TEAM-1"), Set.of(JudgeId.of("J1")));
        round.schedule(first);
        Heat duplicateTeam = heat("H2", round.id(), TeamId.of("TEAM-1"), Set.of(JudgeId.of("J2")));
        assertThrows(InvalidValueException.class, () -> round.schedule(duplicateTeam));
        assertEquals(List.of(first), round.heats().entries());
        assertEquals(RoundOrdinal.of(1), round.ordinal());
        assertThrows(InvalidValueException.class,
                () -> heat("EMPTY", round.id(), TeamId.of("TEAM-2"), Set.of()));
        assertTrue(new ScheduleConflictDetector().detect(List.of(first), first).isEmpty());
    }

    @Test
    void validatesAppealCorrectionAndRunState() {
        Appeal appeal = new Appeal(AppealId.of("A1"), RunId.of("RUN-1"), TeamId.of("TEAM-1"), "claim", NOW);
        assertThrows(InvalidValueException.class,
                () -> new Appeal(AppealId.of("A2"), RunId.of("RUN-1"), TeamId.of("TEAM-1"), null, NOW));
        assertThrows(InvalidValueException.class,
                () -> new Appeal(AppealId.of("A2"), RunId.of("RUN-1"), TeamId.of("TEAM-1"), " ", NOW));
        assertFalse(appeal.isAccepted());
        assertEquals(RunId.of("RUN-1"), appeal.runId());
        assertEquals(TeamId.of("TEAM-1"), appeal.teamId());
        assertEquals("claim", appeal.claim());
        assertEquals(NOW, appeal.submittedAt());

        MeasurementSet measurements = MeasurementSet.empty();
        Actor actor = Actor.of("actor");
        assertThrows(InvalidValueException.class,
                () -> new ResultCorrection(NOW, actor, null, measurements, List.of(), Optional.empty()));
        assertThrows(InvalidValueException.class,
                () -> new ResultCorrection(NOW, actor, " ", measurements, List.of(), Optional.empty()));
        RunResult run = run();
        assertEquals(HeatId.of("HEAT-1"), run.heatId());
        assertEquals(NOW, run.capturedAt());
        assertEquals(AttemptNumber.first(), run.attemptNumber());
        assertThrows(NotFoundException.class, () -> measurements.require(MetricKey.of("MISSING")));
    }

    private ScoringContext measured(MetricKey key, String value) {
        return ScoringContext.of(MeasurementSet.empty().with(key, MetricValue.of(value)));
    }

    private TeamRegistration registration(String name, TeamMembers members) {
        return registration(name, members, TeamFixtures.eligibleRobot());
    }

    private TeamRegistration registration(String name, TeamMembers members, Robot robot) {
        return new TeamRegistration(TeamId.of("TEAM-1"), CompetitionId.of("COMP-1"), CATEGORY.id(), name, members,
                robot, TeamFixtures.completeDocuments());
    }

    private EligibilityRequest request(TeamRegistration registration) {
        return new EligibilityRequest(registration, CATEGORY, TODAY);
    }

    private Competition competition(String name, List<Category> categories) {
        return new Competition(CompetitionId.of("COMP-1"), SeasonId.of("SEASON-1"), name,
                DateRange.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5)), categories);
    }

    private Round round() {
        return new Round(RoundId.of("ROUND-1"), CompetitionId.of("COMP-1"), CategoryId.of("CAT-1"),
                ChallengeId.of("CHALLENGE-1"), RoundOrdinal.of(1), RulebookVersion.first());
    }

    private Heat heat(String id, RoundId roundId, TeamId teamId, Set<JudgeId> judges) {
        return new Heat(HeatId.of(id), roundId, teamId, ArenaId.of("A1"),
                new TimeSlot(LocalDateTime.of(2026, 3, 2, 10, 0), Duration.ofMinutes(15)), judges);
    }

    private RunResult run() {
        return new RunResult(RunId.of("RUN-1"), RoundId.of("ROUND-1"), HeatId.of("HEAT-1"), TeamId.of("TEAM-1"),
                ChallengeId.of("CHALLENGE-1"), RulebookVersion.first(), AttemptNumber.first(), NOW,
                MeasurementSet.empty(), JudgeEvaluations.none(), List.of());
    }
}
