package com.dps.roboleague.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la tabla de {@link AppealRow}. Solo lo usa su adaptador. */
public interface AppealRows extends JpaRepository<AppealRow, String> {

    List<AppealRow> findByRunIdOrderById(String runId);
}
