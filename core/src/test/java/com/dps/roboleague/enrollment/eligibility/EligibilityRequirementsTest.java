package com.dps.roboleague.enrollment.eligibility;

import com.dps.roboleague.enrollment.competition.Category;
import com.dps.roboleague.enrollment.eligibility.rule.AgeRangeRule;
import com.dps.roboleague.enrollment.eligibility.rule.RequiredDocumentsRule;
import com.dps.roboleague.enrollment.eligibility.rule.RobotClassRule;
import com.dps.roboleague.enrollment.eligibility.rule.RobotSpecificationRule;
import com.dps.roboleague.enrollment.eligibility.rule.TeamCompositionRule;
import com.dps.roboleague.enrollment.team.Robot;
import com.dps.roboleague.enrollment.team.TeamDocument;
import com.dps.roboleague.enrollment.team.TeamMembers;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.shared.AgeRange;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityRequirementsTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 3, 1);

    private final Category junior = new Category(CategoryId.of("CAT-1"), "Junior", AgeRange.between(12, 17),
            TeamFixtures.RESCUE_BOT);
    private final EligibilityRequirements requirements = RescueEditionFixture.eligibilityRequirements();

    @Test
    void acceptsATeamThatSatisfiesEveryRule() {
        EligibilityVerdict verdict = evaluate(TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot(),
                TeamFixtures.completeDocuments());

        assertTrue(verdict.isEligible());
        assertTrue(verdict.violations().isEmpty());
    }

    @Test
    void reportsCompetitorsOutsideTheAgeRangeOfTheCategory() {
        EligibilityVerdict verdict = evaluate(TeamFixtures.membersWithUnderageCompetitor(),
                TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments());

        assertFalse(verdict.isEligible());
        assertEquals(List.of(AgeRangeRule.CODE), codesOf(verdict));
    }

    @Test
    void requiresACoachWhenTheTeamHasCompetitorsUnderAge() {
        EligibilityVerdict verdict = evaluate(TeamFixtures.membersWithoutCoach(), TeamFixtures.eligibleRobot(),
                TeamFixtures.completeDocuments());

        assertEquals(List.of(TeamCompositionRule.CODE), codesOf(verdict));
    }

    @Test
    void reportsRobotsThatDoNotMatchTheCategoryClassOrItsLimits() {
        EligibilityVerdict wrongClass = evaluate(TeamFixtures.eligibleMembers(), TeamFixtures.wrongClassRobot(),
                TeamFixtures.completeDocuments());
        EligibilityVerdict overweight = evaluate(TeamFixtures.eligibleMembers(), TeamFixtures.overweightRobot(),
                TeamFixtures.completeDocuments());

        assertEquals(List.of(RobotClassRule.CODE), codesOf(wrongClass));
        assertEquals(List.of(RobotSpecificationRule.CODE), codesOf(overweight));
    }

    @Test
    void collectsEveryViolationInsteadOfStoppingAtTheFirstOne() {
        EligibilityVerdict verdict = evaluate(TeamFixtures.membersWithUnderageCompetitor(),
                TeamFixtures.overweightRobot(), TeamFixtures.incompleteDocuments());

        assertEquals(List.of(AgeRangeRule.CODE, RobotSpecificationRule.CODE, RequiredDocumentsRule.CODE),
                codesOf(verdict));
        assertEquals(3, verdict.reasons().size());
    }

    private EligibilityVerdict evaluate(TeamMembers members, Robot robot, List<TeamDocument> documents) {
        TeamRegistration registration = new TeamRegistration(TeamId.of("TEAM-1"), CompetitionId.of("COMP-1"),
                junior.id(), "Delta Bots", members, robot, documents);
        return requirements.verdictFor(new EligibilityRequest(registration, junior, REFERENCE_DATE));
    }

    private List<EligibilityRuleCode> codesOf(EligibilityVerdict verdict) {
        return verdict.violations().stream().map(EligibilityViolation::ruleCode).distinct().toList();
    }
}
