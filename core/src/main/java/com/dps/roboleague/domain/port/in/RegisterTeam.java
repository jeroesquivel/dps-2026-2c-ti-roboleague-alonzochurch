package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.eligibility.EligibilityVerdict;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.domain.team.MemberRole;
import com.dps.roboleague.domain.team.RegistrationStatus;
import com.dps.roboleague.domain.team.Robot;
import com.dps.roboleague.domain.team.TeamDocument;
import java.time.LocalDate;
import java.util.List;

public interface RegisterTeam {

    Outcome execute(Command command);

    record Command(CompetitionId competitionId, CategoryId categoryId, String teamName, List<MemberDraft> members,
            Robot robot, List<TeamDocument> documents, Actor actor) {
    }

    record MemberDraft(String fullName, LocalDate birthDate, MemberRole role) {
    }

    record Outcome(TeamId teamId, RegistrationStatus status, EligibilityVerdict verdict) {

        public boolean isAccepted() {
            return status == RegistrationStatus.ACCEPTED;
        }
    }
}
