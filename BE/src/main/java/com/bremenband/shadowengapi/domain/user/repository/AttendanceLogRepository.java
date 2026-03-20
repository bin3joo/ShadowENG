package com.bremenband.shadowengapi.domain.user.repository;

import com.bremenband.shadowengapi.domain.user.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    @Query("SELECT a.visitDate FROM AttendanceLog a WHERE a.userId = :userId ORDER BY a.visitDate")
    List<LocalDate> findVisitDatesByUserId(@Param("userId") Long userId);
}
