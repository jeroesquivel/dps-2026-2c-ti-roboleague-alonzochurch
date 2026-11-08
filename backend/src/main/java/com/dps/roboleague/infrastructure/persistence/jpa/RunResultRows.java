package com.dps.roboleague.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la tabla de {@link RunResultRow}. Solo lo usa su adaptador. */
public interface RunResultRows extends JpaRepository<RunResultRow, String> {

    List<RunResultRow> findByRoundIdOrderByAttemptNumberAsc(String roundId);

    List<RunResultRow> findByTeamIdOrderByRoundIdAscAttemptNumberAsc(String teamId);

    boolean existsByRoundIdAndTeamIdAndAttemptNumberAndIdNot(String roundId, String teamId, int attemptNumber,
            String id);
}
