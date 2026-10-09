package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.competition.Category;
import com.dps.roboleague.domain.competition.Competition;
import com.dps.roboleague.domain.competition.CompetitionRepository;
import com.dps.roboleague.domain.competition.Season;
import com.dps.roboleague.domain.competition.SeasonRepository;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.IdGenerator;
import com.dps.roboleague.domain.shared.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CreateCompetitionUseCase implements CreateCompetition {

    private final SeasonRepository seasons;
    private final CompetitionRepository competitions;
    private final RulebookRepository rulebooks;
    private final IdGenerator idGenerator;
    private final AuditLog auditLog;
    private final Clock clock;

    public CreateCompetitionUseCase(SeasonRepository seasons, CompetitionRepository competitions,
            RulebookRepository rulebooks, IdGenerator idGenerator, AuditLog auditLog, Clock clock) {
        this.seasons = seasons;
        this.competitions = competitions;
        this.rulebooks = rulebooks;
        this.idGenerator = idGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        Season season = seasons.findById(command.seasonId())
                .orElseThrow(() -> NotFoundException.of("Season", command.seasonId().value()));
        season.requireCompetitionPeriodInside(command.period());

        CompetitionId id = idGenerator.nextCompetitionId();
        List<Category> categories = command.categories().stream()
                .map(draft -> new Category(idGenerator.nextCategoryId(), draft.name(), draft.ageRange(),
                        draft.robotClass()))
                .toList();
        RulebookVersion version = RulebookVersion.first();
        Rulebook rulebook = Rulebook.of(id, version, LocalDate.now(clock), command.rulebook());
        Competition competition = new Competition(id, season.id(), command.name(), command.period(), categories,
                version);

        rulebooks.save(rulebook);
        competitions.save(competition);
        auditLog.record(AuditEvent.of(clock.instant(), AuditAction.COMPETITION_CREATED, id, command.actor()));
        auditLog.record(new AuditEvent(clock.instant(), AuditAction.RULEBOOK_PUBLISHED, id, command.actor(),
                Map.of(AuditDetail.VERSION, version.toString())));
        return new Result(id, categories.stream().map(Category::id).toList());
    }
}
