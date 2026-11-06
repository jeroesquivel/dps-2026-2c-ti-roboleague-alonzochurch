package com.dps.roboleague.enrollment.eligibility;

import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.RulebookVersion;

/**
 * Puerto de salida de la inscripción: de dónde salen las exigencias de elegibilidad de una competencia. Lo define
 * quien las necesita (la inscripción) y lo implementa quien las conoce (el reglamento), así la inscripción no
 * depende del reglamento.
 */
public interface EligibilityRequirementsSource {

    EligibilityRequirements requirementsOf(CompetitionId competitionId, RulebookVersion version);
}
