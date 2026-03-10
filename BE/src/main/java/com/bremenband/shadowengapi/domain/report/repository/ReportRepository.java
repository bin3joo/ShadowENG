package com.bremenband.shadowengapi.domain.report.repository;

import com.bremenband.shadowengapi.domain.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByStudySession_Id(Long sessionId);
}
