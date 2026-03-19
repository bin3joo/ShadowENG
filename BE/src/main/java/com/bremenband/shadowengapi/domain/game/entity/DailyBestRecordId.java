package com.bremenband.shadowengapi.domain.game.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class DailyBestRecordId implements Serializable {

    private Long userId;
    private int level;
    private LocalDate recordDate;

    public DailyBestRecordId() {}

    public DailyBestRecordId(Long userId, int level, LocalDate recordDate) {
        this.userId = userId;
        this.level = level;
        this.recordDate = recordDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DailyBestRecordId that)) return false;
        return level == that.level
                && Objects.equals(userId, that.userId)
                && Objects.equals(recordDate, that.recordDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, level, recordDate);
    }
}
