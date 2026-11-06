package com.dps.roboleague.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la tabla de {@link CompetitionRow}. Solo lo usa su adaptador. */
public interface CompetitionRows extends JpaRepository<CompetitionRow, String> {
}
