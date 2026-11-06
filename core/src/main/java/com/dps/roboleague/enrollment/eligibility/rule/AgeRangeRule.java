package com.dps.roboleague.enrollment.eligibility.rule;

import com.dps.roboleague.enrollment.eligibility.EligibilityRequest;
import com.dps.roboleague.enrollment.eligibility.EligibilityRule;
import com.dps.roboleague.enrollment.eligibility.EligibilityRuleCode;
import com.dps.roboleague.enrollment.eligibility.EligibilityViolation;
import com.dps.roboleague.enrollment.team.Member;
import com.dps.roboleague.shared.AgeRange;
import java.util.List;

public final class AgeRangeRule implements EligibilityRule {

    public static final EligibilityRuleCode CODE = EligibilityRuleCode.of("AGE_RANGE");

    @Override
    public List<EligibilityViolation> evaluate(EligibilityRequest request) {
        AgeRange range = request.category().ageRange();
        return request.registration().members().competitors().stream()
                .filter(member -> !range.includes(member.ageOn(request.referenceDate())))
                .map(member -> violationFor(member, request))
                .toList();
    }

    private EligibilityViolation violationFor(Member member, EligibilityRequest request) {
        AgeRange range = request.category().ageRange();
        return new EligibilityViolation(CODE, "%s is %d years old and category %s admits ages %d to %d"
                .formatted(member.fullName(), member.ageOn(request.referenceDate()), request.category().name(),
                        range.minimumYears(), range.maximumYears()));
    }
}
