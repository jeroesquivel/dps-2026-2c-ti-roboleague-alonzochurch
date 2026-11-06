package com.dps.roboleague.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la tabla de {@link SeasonRow}. Solo lo usa su adaptador. */
public interface SeasonRows extends JpaRepository<SeasonRow, String> {

    List<SeasonRow> findAllByOrderBySortOrderAsc();
}
