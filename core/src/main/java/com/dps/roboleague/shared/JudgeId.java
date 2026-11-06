package com.dps.roboleague.shared;

public record JudgeId(String value) implements Identifier {

    public JudgeId {
        Identifier.validate(value, "JudgeId");
    }

    public static JudgeId of(String value) {
        return new JudgeId(value);
    }
}
