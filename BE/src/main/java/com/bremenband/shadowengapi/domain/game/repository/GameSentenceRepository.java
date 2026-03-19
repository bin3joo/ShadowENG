package com.bremenband.shadowengapi.domain.game.repository;

import com.bremenband.shadowengapi.domain.game.entity.GameSentence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameSentenceRepository extends JpaRepository<GameSentence, Long> {

    List<GameSentence> findByLevel(int level);
}
