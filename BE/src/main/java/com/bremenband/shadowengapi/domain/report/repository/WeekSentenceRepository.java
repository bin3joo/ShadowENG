package com.bremenband.shadowengapi.domain.report.repository;

import com.bremenband.shadowengapi.domain.report.entity.WeekSentence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeekSentenceRepository extends JpaRepository<WeekSentence, Long> {

    void deleteByReport_Id(Long reportId);

    List<WeekSentence> findByReport_Id(Long reportId);
}
