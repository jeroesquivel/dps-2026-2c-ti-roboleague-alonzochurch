package com.dps.roboleague.domain.shared;

public record MemberId(String value) implements Identifier {

    public MemberId {
        Identifier.validate(value, "MemberId");
    }

    public static MemberId of(String value) {
        return new MemberId(value);
    }
}
