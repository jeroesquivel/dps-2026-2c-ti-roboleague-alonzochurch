package com.dps.roboleague.enrollment.port.in;

import com.dps.roboleague.enrollment.eligibility.EligibilityVerdict;
import com.dps.roboleague.enrollment.team.MemberRole;
import com.dps.roboleague.enrollment.team.RegistrationStatus;
import com.dps.roboleague.enrollment.team.Robot;
import com.dps.roboleague.enrollment.team.TeamDocument;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.TeamId;
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
