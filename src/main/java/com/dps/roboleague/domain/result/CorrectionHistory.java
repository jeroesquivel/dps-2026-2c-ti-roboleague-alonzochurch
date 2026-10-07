package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record CorrectionHistory(List<ResultCorrection> entries) {

    public CorrectionHistory {
        entries = List.copyOf(entries);
        for (int index = 1; index < entries.size(); index++) {
            if (entries.get(index).appliedAt().isBefore(entries.get(index - 1).appliedAt())) {
                throw new InvalidValueException("a correction cannot predate the previous correction");
            }
        }
    }

    public static CorrectionHistory empty() {
        return new CorrectionHistory(List.of());
    }

    public CorrectionHistory append(ResultCorrection correction) {
        List<ResultCorrection> appended = new ArrayList<>(entries);
        appended.add(correction);
        return new CorrectionHistory(appended);
    }

    public Optional<ResultCorrection> latest() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getLast());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
