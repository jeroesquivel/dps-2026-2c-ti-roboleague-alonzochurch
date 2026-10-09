package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import java.util.List;

public interface AcceptAppeal {

    Appeal execute(Command command);

    record Command(AppealId appealId, String rationale, MeasurementSet correctedMeasurements,
            List<IncidentReport> correctedIncidents, Actor reviewer) {
    }
}
