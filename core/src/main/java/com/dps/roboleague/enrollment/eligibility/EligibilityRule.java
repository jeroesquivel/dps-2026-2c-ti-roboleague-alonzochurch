package com.dps.roboleague.enrollment.eligibility;

import java.util.List;

public interface EligibilityRule {

    List<EligibilityViolation> evaluate(EligibilityRequest request);
}
