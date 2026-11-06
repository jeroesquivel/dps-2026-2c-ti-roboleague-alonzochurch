package com.dps.roboleague.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso de Spring Data a la tabla de {@link AuditEventRow}. Solo lo usa su adaptador. */
public interface AuditEventRows extends JpaRepository<AuditEventRow, Long> {

    List<AuditEventRow> findBySubjectTypeAndSubjectIdOrderByIdAsc(String subjectType, String subjectId);
}
