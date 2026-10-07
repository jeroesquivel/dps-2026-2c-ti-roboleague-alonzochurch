package com.dps.roboleague.domain.eligibility.rule;

import com.dps.roboleague.domain.eligibility.EligibilityRequest;
import com.dps.roboleague.domain.eligibility.EligibilityRule;
import com.dps.roboleague.domain.eligibility.EligibilityRuleCode;
import com.dps.roboleague.domain.eligibility.EligibilityViolation;
import com.dps.roboleague.domain.team.Dimensions;
import com.dps.roboleague.domain.team.Robot;
import com.dps.roboleague.domain.team.Weight;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RobotSpecificationRule implements EligibilityRule {

    public static final EligibilityRuleCode CODE = EligibilityRuleCode.of("ROBOT_SPECIFICATION");

    private final Weight maximumWeight;
    private final Dimensions maximumDimensions;

    public RobotSpecificationRule(Weight maximumWeight, Dimensions maximumDimensions) {
        this.maximumWeight = Objects.requireNonNull(maximumWeight, "maximum weight is required");
        this.maximumDimensions = Objects.requireNonNull(maximumDimensions, "maximum dimensions are required");
    }

    @Override
    public List<EligibilityViolation> evaluate(EligibilityRequest request) {
        List<EligibilityViolation> violations = new ArrayList<>();
        Robot robot = request.registration().robot();
        if (robot.weight().exceeds(maximumWeight)) {
            violations.add(new EligibilityViolation(CODE, "robot weighs %s and the limit is %s"
                    .formatted(robot.weight(), maximumWeight)));
        }
        if (!robot.dimensions().fitsWithin(maximumDimensions)) {
            violations.add(new EligibilityViolation(CODE, "robot exceeds the allowed dimensions"));
        }
        return List.copyOf(violations);
    }
}
