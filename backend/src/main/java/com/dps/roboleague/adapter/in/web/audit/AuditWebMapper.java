package com.dps.roboleague.adapter.in.web.audit;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.Identifier;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Traduce entre el contrato público de auditoría y el núcleo. Decide explícitamente los nombres
 * públicos de acciones y detalles: un valor nuevo en {@link AuditAction} o {@link AuditDetail} no
 * compila hasta que se lo clasifica acá, así que la bitácora nunca se expone por accidente.
 */
final class AuditWebMapper {

    /** Prefijo del identificador (el que generan los ids secuenciales) y el tipo de sujeto que designa. */
    private static final Map<String, Function<String, Identifier>> SUBJECTS = Map.of(
            "SEASON", SeasonId::of,
            "COMPETITION", CompetitionId::of,
            "CATEGORY", CategoryId::of,
            "TEAM", TeamId::of,
            "ROUND", RoundId::of,
            "RUN", RunId::of,
            "APPEAL", AppealId::of);

    private AuditWebMapper() {
    }

    /**
     * Resuelve el texto recibido a un sujeto tipado. Un valor ausente o con prefijo desconocido es un
     * error de formato (400); que el sujeto exista o no es otra cuestión y no se valida.
     */
    static Identifier toSubject(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            throw new InvalidValueException("subjectId is required");
        }
        String value = subjectId.trim();
        int separator = value.indexOf('-');
        Function<String, Identifier> factory = separator > 0 ? SUBJECTS.get(value.substring(0, separator)) : null;
        if (factory == null || separator == value.length() - 1) {
            throw new InvalidValueException("subjectId '" + value + "' is not a supported identifier; expected "
                    + "<TYPE>-<n> with TYPE one of " + SUBJECTS.keySet().stream().sorted().toList());
        }
        return factory.apply(value);
    }

    /** La lista conserva el orden de registro entre eventos del mismo instante (el orden de {@code sorted} es estable). */
    static AuditTrailResponse toResponse(Identifier subject, List<AuditEvent> events) {
        return new AuditTrailResponse(subject.value(), events.stream()
                .sorted(Comparator.comparing(AuditEvent::occurredAt))
                .map(AuditWebMapper::toResponse)
                .toList());
    }

    private static AuditEventResponse toResponse(AuditEvent event) {
        Map<String, String> details = new TreeMap<>();
        event.details().forEach((detail, value) -> details.put(name(detail), value));
        return new AuditEventResponse(event.occurredAt().toString(), name(event.action()),
                event.subject().value(), event.actor().name(), details);
    }

    private static String name(AuditAction action) {
        return switch (action) {
            case SEASON_CREATED -> "SEASON_CREATED";
            case COMPETITION_CREATED -> "COMPETITION_CREATED";
            case RULEBOOK_PUBLISHED -> "RULEBOOK_PUBLISHED";
            case TEAM_REGISTERED -> "TEAM_REGISTERED";
            case TEAM_REJECTED -> "TEAM_REJECTED";
            case ROUND_SCHEDULED -> "ROUND_SCHEDULED";
            case RESULT_CAPTURED -> "RESULT_CAPTURED";
            case RESULT_SOURCE_RECEIVED -> "RESULT_SOURCE_RECEIVED";
            case RESULT_CORRECTED -> "RESULT_CORRECTED";
            case STANDINGS_GENERATED -> "STANDINGS_GENERATED";
            case STANDINGS_PUBLISHED -> "STANDINGS_PUBLISHED";
            case STANDINGS_RECALCULATED -> "STANDINGS_RECALCULATED";
            case APPEAL_SUBMITTED -> "APPEAL_SUBMITTED";
            case APPEAL_RESOLVED -> "APPEAL_RESOLVED";
        };
    }

    private static String name(AuditDetail detail) {
        return switch (detail) {
            case RULEBOOK -> "rulebook";
            case VERSION -> "version";
            case VIOLATIONS -> "violations";
            case HEATS -> "heats";
            case MEASUREMENTS -> "measurements";
            case SOURCE -> "source";
            case EVALUATIONS -> "evaluations";
            case INCIDENTS -> "incidents";
            case ORIGINAL -> "original";
            case CORRECTED -> "corrected";
            case REASON -> "reason";
            case REVISION -> "revision";
            case RUN -> "run";
            case STATUS -> "status";
            case RATIONALE -> "rationale";
        };
    }
}
