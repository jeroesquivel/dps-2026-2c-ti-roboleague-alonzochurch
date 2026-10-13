package com.dps.roboleague.adapter.in.web.standings;

import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.PublishStandings;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.ranking.AppliedTiebreak;
import com.dps.roboleague.domain.ranking.PendingRun;
import com.dps.roboleague.domain.ranking.RoundOutcome;
import com.dps.roboleague.domain.ranking.ScoreExplanation;
import com.dps.roboleague.domain.ranking.ScoreSubtotal;
import com.dps.roboleague.domain.ranking.StandingEntry;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.List;
import java.util.function.Function;

/**
 * Traduce entre los DTOs web y los tipos del núcleo. El orden de las entradas, los desempates y la
 * selección de rondas ya vienen decididos por el dominio; acá sólo se proyectan.
 */
final class StandingsWebMapper {

    private StandingsWebMapper() {
    }

    static GenerateStandings.Command toGenerateCommand(String competitionId, String categoryId,
            StandingsActionRequest request) {
        return new GenerateStandings.Command(CompetitionId.of(competitionId), CategoryId.of(categoryId),
                Actor.of(request.actor()));
    }

    static PublishStandings.Command toPublishCommand(String competitionId, String categoryId,
            StandingsActionRequest request) {
        return new PublishStandings.Command(CompetitionId.of(competitionId), CategoryId.of(categoryId),
                Actor.of(request.actor()));
    }

    static RecalculateStandings.Command toRecalculateCommand(String competitionId, String categoryId,
            RecalculateStandingsRequest request) {
        return new RecalculateStandings.Command(CompetitionId.of(competitionId), CategoryId.of(categoryId),
                request.reason(), Actor.of(request.actor()));
    }

    static StandingsResponse toResponse(Standings standings, Function<TeamId, String> teamNames) {
        return new StandingsResponse(standings.competitionId().value(), standings.categoryId().value(),
                standings.revision().number(), standings.status().name(), standings.generatedAt().toString(),
                standings.rulebookVersion().toString(),
                standings.entries().stream().map(entry -> entryOf(entry, teamNames)).toList(),
                standings.pendingRuns().runs().stream().map(run -> pendingRunOf(run, teamNames)).toList());
    }

    static RevisionsResponse toResponse(List<Standings> revisions, Function<TeamId, String> teamNames) {
        return new RevisionsResponse(revisions.stream().map(revision -> toResponse(revision, teamNames)).toList());
    }

    private static StandingsResponse.EntryResponse entryOf(StandingEntry entry, Function<TeamId, String> teamNames) {
        return new StandingsResponse.EntryResponse(entry.position(), entry.teamId().value(),
                teamNames.apply(entry.teamId()), entry.totalPoints().value(),
                entry.appliedTiebreaks().stream().map(StandingsWebMapper::tiebreakOf).toList(),
                explanationOf(entry.explanation()));
    }

    private static StandingsResponse.TiebreakResponse tiebreakOf(AppliedTiebreak tiebreak) {
        return new StandingsResponse.TiebreakResponse(tiebreak.code(), tiebreak.description());
    }

    private static StandingsResponse.ExplanationResponse explanationOf(ScoreExplanation explanation) {
        return new StandingsResponse.ExplanationResponse(
                explanation.subtotals().stream().map(StandingsWebMapper::subtotalOf).toList());
    }

    private static StandingsResponse.SubtotalResponse subtotalOf(ScoreSubtotal subtotal) {
        return new StandingsResponse.SubtotalResponse(subtotal.code(), subtotal.description(),
                subtotal.points().value(), subtotal.rounds().stream().map(StandingsWebMapper::roundOf).toList());
    }

    private static StandingsResponse.RoundResponse roundOf(RoundOutcome round) {
        return new StandingsResponse.RoundResponse(round.ordinal().value(), round.points().value(),
                round.status().name(), round.reason());
    }

    private static StandingsResponse.PendingRunResponse pendingRunOf(PendingRun run,
            Function<TeamId, String> teamNames) {
        return new StandingsResponse.PendingRunResponse(run.runId().value(), run.teamId().value(),
                teamNames.apply(run.teamId()), run.roundId().value(), run.attemptNumber().value(),
                run.missingSources().stream().map(Enum::name).toList());
    }
}
