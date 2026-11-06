package com.dps.roboleague.enrollment.eligibility.rule;

import com.dps.roboleague.enrollment.competition.RobotClass;
import com.dps.roboleague.enrollment.eligibility.EligibilityRequest;
import com.dps.roboleague.enrollment.eligibility.EligibilityRule;
import com.dps.roboleague.enrollment.eligibility.EligibilityRuleCode;
import com.dps.roboleague.enrollment.eligibility.EligibilityViolation;
import java.util.List;

public final class RobotClassRule implements EligibilityRule {

    public static final EligibilityRuleCode CODE = EligibilityRuleCode.of("ROBOT_CLASS");

    @Override
    public List<EligibilityViolation> evaluate(EligibilityRequest request) {
        RobotClass declared = request.registration().robot().robotClass();
        RobotClass expected = request.category().robotClass();
        if (declared.equals(expected)) {
            return List.of();
        }
        return List.of(new EligibilityViolation(CODE, "robot class %s does not match category class %s"
                .formatted(declared.code(), expected.code())));
    }
}
