package com.bremenband.shadowengapi.domain.study.repository;

import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    long countBySentence_Id(Long sentenceId);

    List<Evaluation> findByStudySession_Id(Long sessionId);

    List<Evaluation> findByStudySession_User_IdOrderByCreatedAtAsc(Long userId);
}
