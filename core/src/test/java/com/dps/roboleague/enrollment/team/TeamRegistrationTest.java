package com.dps.roboleague.enrollment.team;

import com.dps.roboleague.enrollment.eligibility.EligibilityVerdict;
import com.dps.roboleague.enrollment.eligibility.EligibilityViolation;
import com.dps.roboleague.enrollment.eligibility.rule.AgeRangeRule;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.TeamFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamRegistrationTest {

    private static final EligibilityViolation UNDERAGE = new EligibilityViolation(AgeRangeRule.CODE,
            "Tim is 8 years old");

    private final TeamRegistration registration = new TeamRegistration(TeamId.of("TEAM-1"),
            CompetitionId.of("COMP-1"), CategoryId.of("CAT-1"), "Delta Bots", TeamFixtures.eligibleMembers(),
            TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments());

    @Test
    void anEligibleVerdictAcceptsTheRegistration() {
        TeamRegistration accepted = registration.resolveWith(new EligibilityVerdict(List.of()));

        assertTrue(accepted.isAccepted());
        assertTrue(accepted.rejectionReasons().isEmpty());
        assertEquals(registration.documentTypes(), accepted.documentTypes());
        assertEquals(CategoryId.of("CAT-1"), accepted.categoryId());
    }

    @Test
    void anIneligibleVerdictRejectsTheRegistrationKeepingWhichRuleFailed() {
        TeamRegistration rejected = registration.resolveWith(new EligibilityVerdict(List.of(UNDERAGE)));

        assertFalse(rejected.isAccepted());
        assertEquals(RegistrationStatus.REJECTED, rejected.status());
        assertEquals(List.of(AgeRangeRule.CODE),
                rejected.rejectionReasons().stream().map(EligibilityViolation::ruleCode).toList());
    }

    @Test
    void anIneligibleTeamCannotEndUpAcceptedByASecondDecision() {
        TeamRegistration rejected = registration.resolveWith(new EligibilityVerdict(List.of(UNDERAGE)));

        assertThrows(ConflictException.class, () -> rejected.resolveWith(new EligibilityVerdict(List.of())));
        assertEquals(RegistrationStatus.REJECTED, rejected.status());
    }

    @Test
    void resolvingReturnsANewRegistrationAndLeavesTheSubmittedOneUntouched() {
        registration.resolveWith(new EligibilityVerdict(List.of()));

        assertEquals(RegistrationStatus.SUBMITTED, registration.status());
        assertFalse(registration.isAccepted());
    }
}
