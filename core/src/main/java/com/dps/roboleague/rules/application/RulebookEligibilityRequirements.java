package com.dps.roboleague.rules.application;

import com.dps.roboleague.enrollment.eligibility.EligibilityRequirements;
import com.dps.roboleague.enrollment.eligibility.EligibilityRequirementsSource;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RulebookVersion;

/** Las exigencias de elegibilidad de una competencia son las del reglamento publicado en esa versión. */
public final class RulebookEligibilityRequirements implements EligibilityRequirementsSource {

    private final RulebookRepository rulebooks;

    public RulebookEligibilityRequirements(RulebookRepository rulebooks) {
        this.rulebooks = rulebooks;
    }

    @Override
    public EligibilityRequirements requirementsOf(CompetitionId competitionId, RulebookVersion version) {
        return rulebooks.find(competitionId, version)
                .orElseThrow(() -> NotFoundException.of("Rulebook", version.toString()))
                .eligibilityRequirements();
    }
}
