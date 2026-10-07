package com.dps.roboleague.domain.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.eligibility.EligibilityVerdict;
import com.dps.roboleague.domain.eligibility.EligibilityViolation;
import com.dps.roboleague.domain.eligibility.rule.AgeRangeRule;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.TeamFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamRegistrationTest {

    private static final EligibilityViolation UNDERAGE = new EligibilityViolation(AgeRangeRule.CODE,
            "Tim is 8 years old");

    private final TeamRegistration registration = new TeamRegistration(TeamId.of("TEAM-1"),
            CompetitionId.of("COMP-1"), CategoryId.of("CAT-1"), "Delta Bots", TeamFixtures.eligibleMembers(),
            TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments());

    @Test
    void anEligibleVerdictAcceptsTheRegistration() {
        registration.resolveWith(new EligibilityVerdict(List.of()));

        assertTrue(registration.isAccepted());
        assertTrue(registration.rejectionReasons().isEmpty());
    }

    @Test
    void anIneligibleVerdictRejectsTheRegistrationKeepingWhichRuleFailed() {
        registration.resolveWith(new EligibilityVerdict(List.of(UNDERAGE)));

        assertFalse(registration.isAccepted());
        assertEquals(RegistrationStatus.REJECTED, registration.status());
        assertEquals(List.of(AgeRangeRule.CODE),
                registration.rejectionReasons().stream().map(EligibilityViolation::ruleCode).toList());
    }

    @Test
    void anIneligibleTeamCannotEndUpAcceptedByASecondDecision() {
        registration.resolveWith(new EligibilityVerdict(List.of(UNDERAGE)));

        assertThrows(ConflictException.class, () -> registration.resolveWith(new EligibilityVerdict(List.of())));
        assertEquals(RegistrationStatus.REJECTED, registration.status());
    }
}
