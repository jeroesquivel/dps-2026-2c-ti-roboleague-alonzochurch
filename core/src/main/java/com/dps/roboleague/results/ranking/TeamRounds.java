package com.dps.roboleague.results.ranking;

import com.dps.roboleague.rules.ranking.AttemptAggregation;
import com.dps.roboleague.rules.ranking.ChallengeRounds;
import com.dps.roboleague.rules.ranking.ExplanationDraft;
import com.dps.roboleague.rules.ranking.PlayedRound;
import com.dps.roboleague.rules.ranking.RoundSelection;
import com.dps.roboleague.rules.ranking.ScoreExplanation;
import com.dps.roboleague.rules.ranking.TeamRuns;
import com.dps.roboleague.shared.ChallengeId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

public record TeamRounds(List<PlayedRound> rounds) {

    public TeamRounds {
        rounds = rounds.stream().sorted(Comparator.comparing(PlayedRound::ordinal)).toList();
    }

    /** Los intentos del equipo, para los desempates; el puntaje de cada ronda lo decide su propia política. */
    public TeamRuns runs(AttemptAggregation aggregation) {
        return new TeamRuns(PlayedRound.attemptsOf(rounds), aggregation);
    }

    /**
     * Explica el puntaje: las rondas de cada desafío se cuentan según la política de la versión del reglamento bajo
     * la que se jugaron, no según la que esté vigente hoy.
     */
    public ScoreExplanation explain() {
        ExplanationDraft draft = new ExplanationDraft();
        rounds.forEach(round -> draft.track(round.policy()));
        rounds.stream()
                .collect(Collectors.groupingBy(round -> new Played(round.challengeId(), round.policy().selection()),
                        LinkedHashMap::new, Collectors.toList()))
                .forEach((key, grouped) -> key.selection().explain(new ChallengeRounds(key.challengeId(), grouped), draft));
        return draft.explanation();
    }

    private record Played(ChallengeId challengeId, RoundSelection selection) {
    }
}
