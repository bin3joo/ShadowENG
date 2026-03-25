package com.bremenband.shadowengapi.domain.study.repository;

import com.bremenband.shadowengapi.domain.study.entity.Sentence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface SentenceRepository extends JpaRepository<Sentence, Long> {

    List<Sentence> findByStudySession_IdOrderByIdAsc(Long sessionId);

    long countByStudySession_Id(Long sessionId);

    @Query("SELECT s.studySession.id, COUNT(s) FROM Sentence s WHERE s.studySession.id IN :sessionIds GROUP BY s.studySession.id")
    List<Object[]> countBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    default Map<Long, Long> countMapBySessionIds(List<Long> sessionIds) {
        return countBySessionIds(sessionIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }
}
