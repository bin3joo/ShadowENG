package com.bremenband.shadowengapi.domain.game.repository;

import com.bremenband.shadowengapi.domain.game.entity.DailyGameSentence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyGameSentenceRepository extends JpaRepository<DailyGameSentence, Long> {

    Optional<DailyGameSentence> findByLevelAndAssignedDate(int level, LocalDate date);

    // 당주(월~일)에 해당 레벨에서 이미 출제된 문장 ID 조회 (중복 방지)
    @Query("SELECT d.gameSentence.id FROM DailyGameSentence d " +
           "WHERE d.level = :level AND d.assignedDate BETWEEN :weekStart AND :weekEnd")
    List<Long> findUsedSentenceIdsByLevelAndWeek(
            @Param("level") int level,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd);
}
