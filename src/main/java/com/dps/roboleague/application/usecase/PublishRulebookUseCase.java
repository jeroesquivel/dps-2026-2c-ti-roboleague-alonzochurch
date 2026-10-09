package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.competition.Competition;
import com.dps.roboleague.domain.competition.CompetitionRepository;
import com.dps.roboleague.domain.port.in.PublishRulebook;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.NotFoundException;
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
