package com.bremenband.shadowengapi.domain.study.repository;

import com.bremenband.shadowengapi.domain.study.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    long countBySentence_Id(Long sentenceId);

    Optional<Evaluation> findTopBySentence_IdOrderByCreatedAtDesc(Long sentenceId);

    Optional<Evaluation> findTopBySentence_IdAndStepOrderByCreatedAtDesc(Long sentenceId, int step);

    List<Evaluation> findByStudySession_Id(Long sessionId);

    List<Evaluation> findByStudySession_IdAndStep(Long sessionId, int step);

    List<Evaluation> findByStudySession_User_IdAndStepOrderByCreatedAtAsc(Long userId, int step);

    @Query("SELECT COUNT(DISTINCT e.sentence.id) FROM Evaluation e WHERE e.studySession.id = :sessionId")
    long countDistinctEvaluatedSentencesBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT e.studySession.id, COUNT(DISTINCT e.sentence.id) FROM Evaluation e WHERE e.studySession.id IN :sessionIds GROUP BY e.studySession.id")
    List<Object[]> countDistinctEvaluatedSentencesBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    @Query("SELECT DISTINCT CAST(e.createdAt AS LocalDate) FROM Evaluation e " +
            "WHERE e.studySession.user.id = :userId ORDER BY CAST(e.createdAt AS LocalDate)")
    List<LocalDate> findDistinctStudyDatesByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT CAST(e.createdAt AS LocalDate) FROM Evaluation e " +
            "WHERE e.studySession.user.id = :userId AND e.step = 4 " +
            "AND CAST(e.createdAt AS LocalDate) >= :from AND CAST(e.createdAt AS LocalDate) <= :to")
    List<LocalDate> findDistinctCompletedDatesInRangeByUserId(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    default Map<Long, Long> completedSentencesMapBySessionIds(List<Long> sessionIds) {
        return countDistinctEvaluatedSentencesBySessionIds(sessionIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }
}
