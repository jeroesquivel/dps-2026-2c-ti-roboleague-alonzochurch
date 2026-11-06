package com.dps.roboleague.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la tabla de {@link RoundRow}. Solo lo usa su adaptador. */
public interface RoundRows extends JpaRepository<RoundRow, String> {

    List<RoundRow> findByCompetitionIdOrderByOrdinalAsc(String competitionId);

    List<RoundRow> findByCompetitionIdAndCategoryIdOrderByOrdinalAsc(String competitionId, String categoryId);
}
