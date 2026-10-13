package com.dps.roboleague.adapter.in.web.appeal;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.appeal.AppealDecision;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.RejectAppeal;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Traduce entre los DTOs web y los tipos del núcleo. Sólo verifica presencia y forma; la validez de
 * la corrección contra el reglamento histórico es decisión de {@code AcceptAppeal}.
 */
final class AppealWebMapper {

    private AppealWebMapper() {
    }

    static SubmitAppeal.Command toSubmitCommand(SubmitAppealRequest request) {
        return new SubmitAppeal.Command(RunId.of(request.runId()), TeamId.of(request.teamId()), request.claim(),
                Actor.of(request.actor()));
    }

    static AcceptAppeal.Command toAcceptCommand(String appealId, AcceptAppealRequest request) {
        return new AcceptAppeal.Command(AppealId.of(appealId), request.rationale(),
                measurementsOf(request.correctedMeasurements()), incidentsOf(request.correctedIncidents()),
                Actor.of(request.reviewer()));
    }

    static RejectAppeal.Command toRejectCommand(String appealId, RejectAppealRequest request) {
        return new RejectAppeal.Command(AppealId.of(appealId), request.rationale(), Actor.of(request.reviewer()));
    }

    static AppealResponse toResponse(Appeal appeal) {
        return new AppealResponse(appeal.id().value(), appeal.runId().value(), appeal.teamId().value(),
                appeal.claim(), appeal.submittedAt().toString(), appeal.status().name(),
                appeal.decision().map(AppealWebMapper::decisionOf).orElse(null));
    }

    private static AppealResponse.DecisionResponse decisionOf(AppealDecision decision) {
        return new AppealResponse.DecisionResponse(decision.reviewer().name(), decision.rationale(),
                decision.decidedAt().toString());
    }

    private static MeasurementSet measurementsOf(Map<String, BigDecimal> corrected) {
        if (corrected == null) {
            throw new InvalidValueException("the complete set of corrected measurements is required");
        }
        MeasurementSet measurements = MeasurementSet.empty();
        for (Map.Entry<String, BigDecimal> measurement : corrected.entrySet()) {
            if (measurement.getValue() == null) {
                throw new InvalidValueException("metric " + measurement.getKey() + " requires a value");
            }
            measurements = measurements.with(MetricKey.of(measurement.getKey()),
                    MetricValue.of(measurement.getValue()));
        }
        return measurements;
    }

    private static List<IncidentReport> incidentsOf(List<AcceptAppealRequest.IncidentRequest> corrected) {
        if (corrected == null) {
            throw new InvalidValueException("the complete list of corrected incidents is required, even if empty");
        }
        return corrected.stream().map(AppealWebMapper::incidentOf).toList();
    }

    private static IncidentReport incidentOf(AcceptAppealRequest.IncidentRequest incident) {
        if (incident == null || incident.occurrences() == null) {
            throw new InvalidValueException("an incident requires a code and its occurrences");
        }
        return new IncidentReport(PenaltyCode.of(incident.code()), incident.occurrences());
    }
}
