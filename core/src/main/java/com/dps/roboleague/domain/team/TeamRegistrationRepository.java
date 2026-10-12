package com.dps.roboleague.domain.team;

import com.dps.roboleague.domain.shared.TeamId;
import java.util.Optional;

public interface TeamRegistrationRepository {

    void save(TeamRegistration registration);

    Optional<TeamRegistration> findById(TeamId id);
}
