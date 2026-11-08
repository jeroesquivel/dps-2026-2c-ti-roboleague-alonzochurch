package com.dps.roboleague.adapter.in.graphql.type;

import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.rules.challenge.SourceBreakdown;
import com.dps.roboleague.rules.challenge.SourcedScore;
import com.dps.roboleague.rules.scoring.BonusCapOutcome;
import com.dps.roboleague.rules.scoring.ScoreBreakdown;
import com.dps.roboleague.rules.scoring.ScoreContribution;
import java.util.List;

public final class ScoreGraphMapper {

    private ScoreGraphMapper() {
    }

    public static ScoreGraph pending(RunResult run) {
        return new ScoreGraph(run.completion().status().name(),
                run.completion().missing().stream().map(Enum::name).toList(), run.rulebookVersion().toString(),
                null, null, null, null);
    }

    public static ScoreGraph complete(CalculateRunScore.RunScore score) {
        return new ScoreGraph(score.completion().status().name(), List.of(), score.rulebookVersion().toString(),
                score.total().value(), contributions(score.breakdown()),
                score.bonusCap().map(ScoreGraphMapper::bonusCap).orElse(null),
                score.bySource().map(ScoreGraphMapper::bySource).orElse(null));
    }

    private static List<ScoreGraph.ContributionGraph> contributions(ScoreBreakdown breakdown) {
        return breakdown.contributions().stream().map(ScoreGraphMapper::contribution).toList();
    }

    private static ScoreGraph.ContributionGraph contribution(ScoreContribution value) {
        return new ScoreGraph.ContributionGraph(value.ruleCode().value(), value.kind().name(), value.explanation(),
                value.points().value());
    }

    private static ScoreGraph.BonusCapGraph bonusCap(BonusCapOutcome value) {
        return new ScoreGraph.BonusCapGraph(value.obtained().value(), value.cap().value(), value.trimmed().value(),
                value.applied().value());
    }

    private static ScoreGraph.BySourceGraph bySource(SourcedScore value) {
        return new ScoreGraph.BySourceGraph(value.sources().stream().map(ScoreGraphMapper::source).toList(),
                new ScoreGraph.GroupGraph(value.acrossSources().total().value(), contributions(value.acrossSources())));
    }

    private static ScoreGraph.SourceGraph source(SourceBreakdown value) {
        return new ScoreGraph.SourceGraph(value.source().name(), value.subtotal().value(),
                contributions(value.breakdown()));
    }
}
