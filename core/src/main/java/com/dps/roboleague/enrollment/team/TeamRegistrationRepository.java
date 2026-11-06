package com.dps.roboleague.enrollment.team;

import com.dps.roboleague.shared.TeamId;
import java.util.Optional;

public interface TeamRegistrationRepository {

    void save(TeamRegistration registration);

    Optional<TeamRegistration> findById(TeamId id);
}
