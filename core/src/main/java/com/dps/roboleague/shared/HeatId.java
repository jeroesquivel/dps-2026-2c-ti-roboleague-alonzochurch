package com.dps.roboleague.shared;

public record HeatId(String value) implements Identifier {

    public HeatId {
        Identifier.validate(value, "HeatId");
    }

    public static HeatId of(String value) {
        return new HeatId(value);
    }
}
