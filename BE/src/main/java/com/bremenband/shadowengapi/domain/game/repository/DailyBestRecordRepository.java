package com.bremenband.shadowengapi.domain.game.repository;

import com.bremenband.shadowengapi.domain.game.entity.DailyBestRecord;
import com.bremenband.shadowengapi.domain.game.entity.DailyBestRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyBestRecordRepository extends JpaRepository<DailyBestRecord, DailyBestRecordId> {

    Optional<DailyBestRecord> findByUserIdAndLevelAndRecordDate(Long userId, int level, LocalDate recordDate);

    List<DailyBestRecord> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);
}
