package com.dps.roboleague.results.result;

import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.support.ShowcaseFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
