package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.competition.Category;
import com.dps.roboleague.domain.competition.Competition;
import com.dps.roboleague.domain.competition.CompetitionRepository;
import com.dps.roboleague.domain.eligibility.EligibilityRequest;
import com.dps.roboleague.domain.eligibility.EligibilityVerdict;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.IdGenerator;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.team.Member;
import com.dps.roboleague.domain.team.TeamMembers;
import com.dps.roboleague.domain.team.TeamRegistration;
import com.dps.roboleague.domain.team.TeamRegistrationRepository;
import java.time.Clock;
import java.util.Map;

public final class RegisterTeamUseCase implements RegisterTeam {

    private final CompetitionRepository competitions;
    private final RulebookRepository rulebooks;
    private final TeamRegistrationRepository registrations;
    private final IdGenerator idGenerator;
    private final AuditLog auditLog;
    private final Clock clock;

    public RegisterTeamUseCase(CompetitionRepository competitions, RulebookRepository rulebooks,
            TeamRegistrationRepository registrations, IdGenerator idGenerator, AuditLog auditLog, Clock clock) {
        this.competitions = competitions;
        this.rulebooks = rulebooks;
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
        Rulebook rulebook = rulebooks.find(competition.id(), version)
                .orElseThrow(() -> NotFoundException.of("Rulebook", version.toString()));

        TeamRegistration submitted = new TeamRegistration(idGenerator.nextTeamId(), competition.id(),
                category.id(), command.teamName(), membersOf(command), command.robot(), command.documents());

        EligibilityVerdict verdict = rulebook.eligibilityRequirements()
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
