package com.dps.roboleague.adapter.in.graphql.type;

import com.dps.roboleague.enrollment.port.in.FindCompetition;

/** Traduce la vista de una competencia del núcleo a los tipos GraphQL. No decide nada: sólo cambia de forma. */
public final class CompetitionGraphMapper {

    private CompetitionGraphMapper() {
    }

    public static CompetitionGraph toGraph(FindCompetition.View view) {
        return new CompetitionGraph(view.id().value(), view.name(),
                new CompetitionGraph.PeriodGraph(view.period().start().toString(), view.period().end().toString()),
                view.activeRulebookVersion().toString(),
                view.categories().stream().map(category -> toGraph(view, category)).toList());
    }

    private static CategoryGraph toGraph(FindCompetition.View competition, FindCompetition.CategoryView category) {
        return new CategoryGraph(competition.id().value(), category.id().value(), category.name(),
                new CategoryGraph.AgeRangeGraph(category.ageRange().minimumYears(),
                        category.ageRange().maximumYears()),
                category.robotClass().code());
    }
}
