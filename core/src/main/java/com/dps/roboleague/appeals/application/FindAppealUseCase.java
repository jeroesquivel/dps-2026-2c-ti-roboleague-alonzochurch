package com.dps.roboleague.appeals.application;

import com.dps.roboleague.appeals.Appeal;
import com.dps.roboleague.appeals.AppealRepository;
import com.dps.roboleague.appeals.port.in.FindAppeal;
import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.NotFoundException;

public final class FindAppealUseCase implements FindAppeal {

    private final AppealRepository appeals;

    public FindAppealUseCase(AppealRepository appeals) {
        this.appeals = appeals;
    }

    @Override
    public Appeal execute(AppealId appealId) {
        return appeals.findById(appealId)
                .orElseThrow(() -> NotFoundException.of("Appeal", appealId.value()));
    }
}
