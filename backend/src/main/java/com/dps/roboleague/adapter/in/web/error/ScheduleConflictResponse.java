package com.dps.roboleague.adapter.in.web.error;

import java.util.List;

/** Error de agenda: además del mensaje, enumera cada conflicto con su tipo estable. */
public record ScheduleConflictResponse(String code, String message, List<ConflictDetail> conflicts) {

    public record ConflictDetail(String type, String detail) {
    }
}
