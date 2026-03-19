package com.bremenband.shadowengapi.domain.game.repository;

import com.bremenband.shadowengapi.domain.game.entity.Tier;
import com.bremenband.shadowengapi.domain.game.entity.UserGameProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserGameProfileRepository extends JpaRepository<UserGameProfile, Long> {

    Optional<UserGameProfile> findByUser_Id(Long userId);

    // 특정 티어의 비동결 유저 — 주간 점수 내림차순 (리더보드 및 티어 계산용)
    @Query("SELECT p FROM UserGameProfile p JOIN FETCH p.user " +
           "WHERE p.tier = :tier AND p.frozen = false " +
           "ORDER BY p.weeklyScore DESC")
    List<UserGameProfile> findByTierAndNotFrozenOrderByWeeklyScoreDesc(@Param("tier") Tier tier);

    // 스케줄러: 전체 프로필 조회
    @Query("SELECT p FROM UserGameProfile p JOIN FETCH p.user")
    List<UserGameProfile> findAllWithUser();
}
