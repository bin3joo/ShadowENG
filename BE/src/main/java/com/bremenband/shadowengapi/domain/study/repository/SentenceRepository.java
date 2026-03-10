package com.bremenband.shadowengapi.domain.study.repository;

import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SentenceRepository extends JpaRepository<Sentence, Long> {

    List<Sentence> findByStudySession_Id(Long sessionId);
}
