package com.bremenband.shadowengapi.domain.game.repository;

import com.bremenband.shadowengapi.domain.game.entity.DailyBestRecord;
import com.bremenband.shadowengapi.domain.game.entity.DailyBestRecordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyBestRecordRepository extends JpaRepository<DailyBestRecord, DailyBestRecordId> {

    Optional<DailyBestRecord> findByUserIdAndLevelAndRecordDate(Long userId, int level, LocalDate recordDate);

    List<DailyBestRecord> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);

    @Query("SELECT MAX(d.bestFinalScore) FROM DailyBestRecord d " +
            "WHERE d.userId = :userId AND d.level = :level AND d.recordDate BETWEEN :from AND :to")
    Optional<BigDecimal> findWeeklyBestScore(@Param("userId") Long userId, @Param("level") int level,
                                             @Param("from") LocalDate from, @Param("to") LocalDate to);
}
