package com.bremenband.shadowengapi.domain.game.repository;

import com.bremenband.shadowengapi.domain.game.entity.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {

    List<GameRecord> findByUser_IdAndPlayedDateOrderByCreatedAtDesc(Long userId, LocalDate date);
}
