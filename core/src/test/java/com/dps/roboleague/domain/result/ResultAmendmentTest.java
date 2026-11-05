package com.dps.roboleague.domain.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.support.ShowcaseFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultAmendmentTest {

    private static final JudgeEvaluations CURRENT = ShowcaseFixture.exampleEvaluations();
    private static final JudgeEvaluations CORRECTED = ShowcaseFixture.evaluations(10, 10, 10, 10);

    @Test
    void anAmendmentThatDoesNotMentionTheJudgesKeepsTheirEvaluations() {
        ResultAmendment amendment = ResultAmendment.of(ShowcaseFixture.exampleMeasurements(), List.of());

        assertEquals(CURRENT, amendment.evaluationsOver(CURRENT));
        assertEquals(CURRENT, EvaluationsChange.keep().over(CURRENT));
    }

    @Test
    void replacingTheEvaluationsDiscardsTheCurrentOnes() {
        ResultAmendment amendment = ResultAmendment.of(ShowcaseFixture.exampleMeasurements(), List.of())
                .replacingEvaluations(CORRECTED);

        assertEquals(CORRECTED, amendment.evaluationsOver(CURRENT));
        assertEquals(ShowcaseFixture.exampleMeasurements(), amendment.measurements());
    }

    @Test
    void anAmendmentRequiresEveryPartOfTheResult() {
        assertThrows(NullPointerException.class, () -> ResultAmendment.of(null, List.of()));
        assertThrows(NullPointerException.class, () -> ResultAmendment.of(MeasurementSet.empty(), null));
        assertThrows(NullPointerException.class,
                () -> new ResultAmendment(MeasurementSet.empty(), List.of(), null));
        assertThrows(NullPointerException.class, () -> EvaluationsChange.replaceWith(null));
    }

    @Test
    void eachOriginDescribesWhyTheResultChanged() {
        CorrectionOrigin appeal = new AcceptedAppeal(AppealId.of("APPEAL-1"));
        CorrectionOrigin entryError = new DataEntryError();

        assertEquals("APPEAL", appeal.kind());
        assertEquals("appeal APPEAL-1 accepted", appeal.description());
        assertEquals("DATA_ENTRY_ERROR", entryError.kind());
        assertEquals("data entry error", entryError.description());
        assertThrows(NullPointerException.class, () -> new AcceptedAppeal(null));
    }
}
