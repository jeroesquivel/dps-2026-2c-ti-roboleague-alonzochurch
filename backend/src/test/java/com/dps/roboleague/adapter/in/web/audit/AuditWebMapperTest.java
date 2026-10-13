package com.dps.roboleague.adapter.in.web.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.SeasonId;
import com.dps.roboleague.domain.shared.TeamId;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditWebMapperTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Actor ACTOR = Actor.of("someone");
    private static final RunId RUN = RunId.of("RUN-1");

    @Test
    void resolvesEachSupportedPrefixToItsTypedIdentifier() {
        assertInstanceOf(SeasonId.class, AuditWebMapper.toSubject("SEASON-1"));
        assertInstanceOf(CompetitionId.class, AuditWebMapper.toSubject("COMPETITION-1"));
        assertInstanceOf(CategoryId.class, AuditWebMapper.toSubject("CATEGORY-1"));
        assertInstanceOf(TeamId.class, AuditWebMapper.toSubject("TEAM-1"));
        assertInstanceOf(RoundId.class, AuditWebMapper.toSubject("ROUND-1"));
        assertInstanceOf(RunId.class, AuditWebMapper.toSubject("RUN-1"));
        assertInstanceOf(AppealId.class, AuditWebMapper.toSubject("APPEAL-1"));
        assertEquals(RUN, AuditWebMapper.toSubject("  RUN-1  "));
    }

    @Test
    void refusesWhatIsNotASupportedIdentifier() {
        for (String subject : new String[] {null, "", "  ", "RUN", "RUN-", "-1", "run-1", "HEAT-1", "X-RUN-1"}) {
            assertThrows(InvalidValueException.class, () -> AuditWebMapper.toSubject(subject), String.valueOf(subject));
        }
    }

    @Test
    void ordersByInstantKeepingTheRecordingOrderOfEventsAtTheSameInstant() {
        AuditEvent late = AuditEvent.of(T0.plusSeconds(5), AuditAction.RESULT_CORRECTED, RUN, ACTOR);
        AuditEvent firstTie = AuditEvent.of(T0, AuditAction.APPEAL_RESOLVED, RUN, Actor.of("first"));
        AuditEvent secondTie = AuditEvent.of(T0, AuditAction.RESULT_CORRECTED, RUN, Actor.of("second"));
        AuditEvent early = AuditEvent.of(T0.minusSeconds(5), AuditAction.RESULT_CAPTURED, RUN, ACTOR);

        AuditTrailResponse trail = AuditWebMapper.toResponse(RUN, List.of(late, firstTie, secondTie, early));

        assertEquals(List.of("RESULT_CAPTURED", "APPEAL_RESOLVED", "RESULT_CORRECTED", "RESULT_CORRECTED"),
                trail.events().stream().map(AuditEventResponse::action).toList());
        assertEquals(List.of("someone", "first", "second", "someone"),
                trail.events().stream().map(AuditEventResponse::actor).toList());
        assertEquals("RUN-1", trail.subjectId());
    }

    @Test
    void mapsEveryActionAndEveryDetailToAPublicName() {
        Map<AuditDetail, String> allDetails = new EnumMap<>(AuditDetail.class);
        Arrays.stream(AuditDetail.values()).forEach(detail -> allDetails.put(detail, detail.name() + "-value"));

        List<AuditEvent> events = Arrays.stream(AuditAction.values())
                .map(action -> new AuditEvent(T0, action, RUN, ACTOR, allDetails)).toList();
        AuditTrailResponse trail = AuditWebMapper.toResponse(RUN, events);

        assertEquals(AuditAction.values().length, trail.events().size());
        for (int i = 0; i < events.size(); i++) {
            AuditEventResponse response = trail.events().get(i);
            assertEquals(events.get(i).action().name(), response.action());
            assertEquals(T0.toString(), response.occurredAt());
            assertEquals(AuditDetail.values().length, response.details().size());
            assertTrue(response.details().keySet().stream().noneMatch(String::isBlank));
            assertFalse(response.details().containsKey("RUN"), "detail names are the public ones");
        }
        assertEquals("RUN-value", trail.events().getFirst().details().get("run"));
    }

    @Test
    void anEmptyTrailHasNoEvents() {
        assertTrue(AuditWebMapper.toResponse(RUN, List.of()).events().isEmpty());
    }
}
