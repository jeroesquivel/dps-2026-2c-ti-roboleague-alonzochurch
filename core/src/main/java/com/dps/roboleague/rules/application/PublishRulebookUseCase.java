package com.dps.roboleague.rules.application;

import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.rules.port.in.PublishRulebook;
import com.dps.roboleague.rules.rulebook.Rulebook;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

public final class PublishRulebookUseCase implements PublishRulebook {

    private final CompetitionRepository competitions;
    private final RulebookRepository rulebooks;
    private final AuditLog auditLog;
    private final Clock clock;

    public PublishRulebookUseCase(CompetitionRepository competitions, RulebookRepository rulebooks, AuditLog auditLog,
            Clock clock) {
        this.competitions = competitions;
        this.rulebooks = rulebooks;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public RulebookVersion execute(Command command) {
        Competition competition = competitions.findById(command.competitionId())
                .orElseThrow(() -> NotFoundException.of("Competition", command.competitionId().value()));

        RulebookVersion version = competition.activeRulebookVersion().next();
        Rulebook rulebook = Rulebook.of(competition.id(), version, LocalDate.now(clock), command.rulebook());
        Competition activated = competition.activateRulebook(version);

        rulebooks.save(rulebook);
        competitions.save(activated);
        auditLog.record(new AuditEvent(clock.instant(), AuditAction.RULEBOOK_PUBLISHED, competition.id(),
                command.actor(), Map.of(AuditDetail.VERSION, version.toString())));
        return version;
    }
}
