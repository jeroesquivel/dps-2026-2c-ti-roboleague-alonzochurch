package com.dps.roboleague.enrollment.application;

import com.dps.roboleague.enrollment.competition.Category;
import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.enrollment.eligibility.EligibilityRequest;
import com.dps.roboleague.enrollment.eligibility.EligibilityRequirementsSource;
import com.dps.roboleague.enrollment.eligibility.EligibilityVerdict;
import com.dps.roboleague.enrollment.port.in.RegisterTeam;
import com.dps.roboleague.enrollment.team.Member;
import com.dps.roboleague.enrollment.team.TeamMembers;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.IdGenerator;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;
import java.util.Map;

public final class RegisterTeamUseCase implements RegisterTeam {

    private final CompetitionRepository competitions;
    private final EligibilityRequirementsSource requirements;
    private final TeamRegistrationRepository registrations;
    private final IdGenerator idGenerator;
    private final AuditLog auditLog;
    private final Clock clock;

    public RegisterTeamUseCase(CompetitionRepository competitions, EligibilityRequirementsSource requirements,
            TeamRegistrationRepository registrations, IdGenerator idGenerator, AuditLog auditLog, Clock clock) {
        this.competitions = competitions;
        this.requirements = requirements;
        this.registrations = registrations;
        this.idGenerator = idGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public Outcome execute(Command command) {
        Competition competition = competitions.findById(command.competitionId())
                .orElseThrow(() -> NotFoundException.of("Competition", command.competitionId().value()));
        Category category = competition.category(command.categoryId());
        RulebookVersion version = competition.activeRulebookVersion();

        TeamRegistration submitted = new TeamRegistration(idGenerator.nextTeamId(), competition.id(),
                category.id(), command.teamName(), membersOf(command), command.robot(), command.documents());

        EligibilityVerdict verdict = requirements.requirementsOf(competition.id(), version)
                .verdictFor(new EligibilityRequest(submitted, category, competition.period().start()));
        TeamRegistration registration = submitted.resolveWith(verdict);
        registrations.save(registration);
        auditLog.record(auditEventFor(registration, verdict, version, command.actor()));
        return new Outcome(registration.id(), registration.status(), verdict);
    }

    private TeamMembers membersOf(Command command) {
        return new TeamMembers(command.members().stream()
                .map(draft -> new Member(idGenerator.nextMemberId(), draft.fullName(), draft.birthDate(), draft.role()))
                .toList());
    }

    private AuditEvent auditEventFor(TeamRegistration registration, EligibilityVerdict verdict,
            RulebookVersion version, Actor actor) {
        AuditAction action = registration.isAccepted() ? AuditAction.TEAM_REGISTERED : AuditAction.TEAM_REJECTED;
        Map<AuditDetail, String> details = Map.of(AuditDetail.RULEBOOK, version.toString(),
                AuditDetail.VIOLATIONS, String.join(" | ", verdict.reasons()));
        return new AuditEvent(clock.instant(), action, registration.id(), actor, details);
    }
}
