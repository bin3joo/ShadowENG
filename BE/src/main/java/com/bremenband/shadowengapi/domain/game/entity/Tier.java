package com.bremenband.shadowengapi.domain.game.entity;

public enum Tier {
    BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, RUBY, CHALLENGER;

    private static final Tier[] VALUES = values();

    public Tier promote() {
        return ordinal() < VALUES.length - 1 ? VALUES[ordinal() + 1] : this;
    }

    public Tier demote() {
        return ordinal() > 0 ? VALUES[ordinal() - 1] : this;
    }

    public boolean isLowest() { return this == BRONZE; }
    public boolean isHighest() { return this == CHALLENGER; }
}
