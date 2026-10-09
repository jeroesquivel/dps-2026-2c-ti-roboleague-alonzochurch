package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.rulebook.RulebookDraft;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.CompetitionId;

public interface PublishRulebook {

    RulebookVersion execute(Command command);

    record Command(CompetitionId competitionId, RulebookDraft rulebook, Actor actor) {
    }
}
