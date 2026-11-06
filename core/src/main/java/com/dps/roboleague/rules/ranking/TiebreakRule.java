package com.dps.roboleague.rules.ranking;

import java.util.Comparator;

public interface TiebreakRule extends Comparator<TeamScoreSummary> {

    String code();

    String description();
}
