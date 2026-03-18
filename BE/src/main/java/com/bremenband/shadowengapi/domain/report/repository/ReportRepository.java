package com.bremenband.shadowengapi.domain.report.repository;

import com.bremenband.shadowengapi.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByStudySession_IdOrderByCreatedAtDesc(Long sessionId);

    Optional<Report> findByIdAndStudySession_Id(Long reportId, Long sessionId);
}
