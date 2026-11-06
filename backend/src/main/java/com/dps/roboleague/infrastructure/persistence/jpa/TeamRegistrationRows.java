package com.dps.roboleague.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la tabla de {@link TeamRegistrationRow}. Solo lo usa su adaptador. */
public interface TeamRegistrationRows extends JpaRepository<TeamRegistrationRow, String> {
}
