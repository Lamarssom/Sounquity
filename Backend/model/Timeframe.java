package com.musicinvestment.musicapp.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Timeframe {
    ONE_MINUTE("1m"),
    FIVE_MINUTES("5m"),
    FIFTEEN_MINUTES("15m"),
    THIRTY_MINUTES("30m"),
    ONE_HOUR("1H"),
    FOUR_HOURS("4H"),
    ONE_DAY("1D"),
    ONE_WEEK("1W");

    private final String value;

    Timeframe(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static Timeframe fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (Timeframe timeframe : Timeframe.values()) {
            if (timeframe.value.equalsIgnoreCase(value)) {
                return timeframe;
            }
        }
        throw new IllegalArgumentException("Unknown timeframe value: " + value);
    }
}