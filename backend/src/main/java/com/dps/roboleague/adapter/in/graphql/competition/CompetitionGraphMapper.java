package com.dps.roboleague.adapter.in.graphql.competition;

import com.dps.roboleague.enrollment.port.in.FindCompetition;

/** Traduce la vista de una competencia del núcleo al tipo GraphQL. No decide nada: sólo cambia de forma. */
final class CompetitionGraphMapper {

    private CompetitionGraphMapper() {
    }

    static CompetitionGraph toGraph(FindCompetition.View view) {
        return new CompetitionGraph(view.id().value(), view.name(),
                new CompetitionGraph.PeriodGraph(view.period().start().toString(), view.period().end().toString()),
                view.activeRulebookVersion().toString(),
                view.categories().stream().map(CompetitionGraphMapper::toGraph).toList());
    }

    private static CompetitionGraph.CategoryGraph toGraph(FindCompetition.CategoryView category) {
        return new CompetitionGraph.CategoryGraph(category.id().value(), category.name(),
                new CompetitionGraph.AgeRangeGraph(category.ageRange().minimumYears(),
                        category.ageRange().maximumYears()),
                category.robotClass().code());
    }
}
