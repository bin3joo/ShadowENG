package com.bremenband.shadowengapi.domain.study.repository;

import com.bremenband.shadowengapi.domain.study.entity.SessionStatus;
import com.bremenband.shadowengapi.domain.study.entity.StudySession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    // video JOIN FETCH: 루프 내 session.getVideo() 호출로 인한 N+1 방지
    @EntityGraph(attributePaths = {"video"})
    List<StudySession> findByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, SessionStatus status);

    // video JOIN FETCH: session.getVideo().getVideoId() 별도 쿼리 방지
    @EntityGraph(attributePaths = {"video"})
    Optional<StudySession> findTopByUser_IdAndStatusOrderByCreatedAtDesc(Long userId, SessionStatus status);

    // video JOIN FETCH: getStudySession()에서 video 다수 필드 접근 시 별도 쿼리 방지
    // user는 getId()만 사용 → Hibernate 6에서 @Id는 프록시 초기화 없이 반환되므로 JOIN 불필요
    @EntityGraph(attributePaths = {"video"})
    @Query("SELECT s FROM StudySession s WHERE s.id = :id")
    Optional<StudySession> findByIdWithVideo(@Param("id") Long id);
}
