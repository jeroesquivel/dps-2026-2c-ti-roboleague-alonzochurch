package com.dps.roboleague.rules.port.in;

import com.dps.roboleague.rules.rulebook.RulebookDraft;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.RulebookVersion;

public interface PublishRulebook {

    RulebookVersion execute(Command command);

    record Command(CompetitionId competitionId, RulebookDraft rulebook, Actor actor) {
    }
}
