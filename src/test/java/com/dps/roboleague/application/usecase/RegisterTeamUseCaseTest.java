package com.dps.roboleague.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.eligibility.EligibilityVerdict;
import com.dps.roboleague.domain.eligibility.rule.AgeRangeRule;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.team.RegistrationStatus;
import com.dps.roboleague.domain.team.TeamRegistration;
import com.dps.roboleague.support.TeamFixtures;
import com.dps.roboleague.support.TestEdition;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegisterTeamUseCaseTest {

    private final TestEdition edition = TestEdition.start();

    @Test
    void acceptsATeamThatSatisfiesTheEligibilityRequirementsOfTheActiveRulebook() {
        RegisterTeam.Outcome outcome = edition.register("Delta Bots", TeamFixtures.eligibleMembers(),
                TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments());

        assertTrue(outcome.isAccepted());
        assertTrue(outcome.verdict().isEligible());
        assertEquals(List.of(AuditAction.TEAM_REGISTERED), edition.auditActionsFor(outcome.teamId()));
    }

    @Test
    void storesTheRejectionReasonsOfATeamThatBreaksTheRules() {
        RegisterTeam.Outcome outcome = edition.register("Rookies", TeamFixtures.membersWithUnderageCompetitor(),
                TeamFixtures.eligibleRobot(), TeamFixtures.incompleteDocuments());

        TeamRegistration stored = edition.registration(outcome.teamId());

        assertFalse(outcome.isAccepted());
        assertEquals(RegistrationStatus.REJECTED, stored.status());
        assertEquals(2, stored.rejectionReasons().size());
        assertEquals(AgeRangeRule.CODE, stored.rejectionReasons().getFirst().ruleCode());
        assertEquals(List.of(AuditAction.TEAM_REJECTED), edition.auditActionsFor(outcome.teamId()));
    }

    @Test
    void aRegistrationIsDecidedOnlyOnce() {
        TeamRegistration rejected = edition.registration(edition.register("Rookies",
                TeamFixtures.membersWithUnderageCompetitor(), TeamFixtures.eligibleRobot(),
                TeamFixtures.completeDocuments()).teamId());

        ConflictException error = assertThrows(ConflictException.class,
                () -> rejected.resolveWith(new EligibilityVerdict(List.of())));

        assertTrue(error.getMessage().contains("already resolved"));
        assertEquals(RegistrationStatus.REJECTED, rejected.status());
        assertFalse(rejected.rejectionReasons().isEmpty());
    }
}
