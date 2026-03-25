package com.bremenband.shadowengapi.domain.report.repository;

import com.bremenband.shadowengapi.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStudySession_IdOrderByCreatedAtDesc(Long sessionId);

    List<Report> findByStudySession_User_IdOrderByCreatedAtDesc(Long userId);

    Optional<Report> findByIdAndStudySession_Id(Long reportId, Long sessionId);

    @Query("SELECT r FROM Report r JOIN FETCH r.studySession s JOIN FETCH s.video WHERE s.user.id = :userId ORDER BY r.createdAt ASC")
    List<Report> findAllByUserIdWithVideoOrderByCreatedAtAsc(@Param("userId") Long userId);
}
