package com.dps.roboleague.results.result;

import com.dps.roboleague.shared.AppealId;
import java.util.Objects;

/** La corrección es consecuencia de haber aceptado una apelación. */
public record AcceptedAppeal(AppealId appealId) implements CorrectionOrigin {

    public static final String KIND = "APPEAL";

    public AcceptedAppeal {
        Objects.requireNonNull(appealId, "appeal id is required");
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String description() {
        return "appeal " + appealId.value() + " accepted";
    }
}
