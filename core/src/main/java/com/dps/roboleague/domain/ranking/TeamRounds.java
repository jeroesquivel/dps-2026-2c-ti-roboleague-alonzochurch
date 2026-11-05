package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.ChallengeId;
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
        rounds.stream()
                .collect(Collectors.groupingBy(round -> new Played(round.challengeId(), round.policy().version()),
                        LinkedHashMap::new, Collectors.toList()))
                .forEach((played, grouped) -> {
                    RoundPolicy policy = grouped.getFirst().policy();
                    draft.track(policy);
                    policy.selection().explain(new ChallengeRounds(played.challengeId(), policy, grouped), draft);
                });
        return draft.explanation();
    }

    private record Played(ChallengeId challengeId, RulebookVersion version) {
    }
}
