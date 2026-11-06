package com.dps.roboleague.support;

import com.dps.roboleague.enrollment.competition.RobotClass;
import com.dps.roboleague.enrollment.team.Dimensions;
import com.dps.roboleague.enrollment.team.DocumentType;
import com.dps.roboleague.enrollment.team.Member;
import com.dps.roboleague.enrollment.team.MemberRole;
import com.dps.roboleague.enrollment.team.Robot;
import com.dps.roboleague.enrollment.team.TeamDocument;
import com.dps.roboleague.enrollment.team.TeamMembers;
import com.dps.roboleague.enrollment.team.Weight;
import com.dps.roboleague.shared.MemberId;
import java.time.LocalDate;
import java.util.List;

public final class TeamFixtures {

    public static final RobotClass RESCUE_BOT = RobotClass.of("RESCUE_BOT");

    private TeamFixtures() {
    }

    public static TeamMembers eligibleMembers() {
        return TeamMembers.of(
                member("M-1", "Ada", LocalDate.of(2010, 5, 20), MemberRole.COMPETITOR),
                member("M-2", "Linus", LocalDate.of(2011, 8, 3), MemberRole.COMPETITOR),
                member("M-3", "Grace", LocalDate.of(1988, 2, 10), MemberRole.COACH));
    }

    public static TeamMembers membersWithUnderageCompetitor() {
        return TeamMembers.of(
                member("M-1", "Ada", LocalDate.of(2010, 5, 20), MemberRole.COMPETITOR),
                member("M-2", "Tim", LocalDate.of(2018, 1, 15), MemberRole.COMPETITOR),
                member("M-3", "Grace", LocalDate.of(1988, 2, 10), MemberRole.COACH));
    }

    public static TeamMembers membersWithoutCoach() {
        return TeamMembers.of(
                member("M-1", "Ada", LocalDate.of(2010, 5, 20), MemberRole.COMPETITOR),
                member("M-2", "Linus", LocalDate.of(2011, 8, 3), MemberRole.COMPETITOR));
    }

    public static Member member(String id, String fullName, LocalDate birthDate, MemberRole role) {
        return new Member(MemberId.of(id), fullName, birthDate, role);
    }

    public static Robot eligibleRobot() {
        return new Robot("Rescuer", RESCUE_BOT, Weight.ofKilograms("2.400"), new Dimensions(180, 180, 150));
    }

    public static Robot overweightRobot() {
        return new Robot("Heavy", RESCUE_BOT, Weight.ofKilograms("4.100"), new Dimensions(180, 180, 150));
    }

    public static Robot wrongClassRobot() {
        return new Robot("Sumo", RobotClass.of("SUMO"), Weight.ofKilograms("2.000"), new Dimensions(180, 180, 150));
    }

    public static List<TeamDocument> completeDocuments() {
        return List.of(new TeamDocument(DocumentType.PARENTAL_CONSENT, "PC-1"),
                new TeamDocument(DocumentType.TECHNICAL_SHEET, "TS-1"));
    }

    public static List<TeamDocument> incompleteDocuments() {
        return List.of(new TeamDocument(DocumentType.PARENTAL_CONSENT, "PC-1"));
    }
}
