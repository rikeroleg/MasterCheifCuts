package com.masterchefcuts.enums;

public enum AnimalType {
    BEEF,
    PORK,
    LAMB;

    public static AnimalType fromString(String value) {
        if (value == null) return null;
        switch (value.trim().toUpperCase()) {
            case "BEEF": return BEEF;
            case "PORK": return PORK;
            case "LAMB": return LAMB;
            default: throw new IllegalArgumentException("Unknown animal type: " + value);
        }
    }
}
