package com.dps.roboleague.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la tabla de {@link RulebookRow}. Solo lo usa su adaptador. */
public interface RulebookRows extends JpaRepository<RulebookRow, String> {
}
