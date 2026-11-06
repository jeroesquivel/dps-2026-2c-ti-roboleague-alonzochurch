package com.dps.roboleague.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la tabla de {@link StandingsRow}. Solo lo usa su adaptador. */
public interface StandingsRows extends JpaRepository<StandingsRow, String> {

    List<StandingsRow> findByCompetitionIdAndCategoryIdOrderByRevisionAsc(String competitionId, String categoryId);

    Optional<StandingsRow> findFirstByCompetitionIdAndCategoryIdOrderByRevisionDesc(String competitionId,
            String categoryId);
}
