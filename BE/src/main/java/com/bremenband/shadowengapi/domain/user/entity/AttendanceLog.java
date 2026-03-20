package com.bremenband.shadowengapi.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "attendance_logs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "visit_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    private AttendanceLog(Long userId, LocalDate visitDate) {
        this.userId = userId;
        this.visitDate = visitDate;
    }

    public static AttendanceLog of(Long userId, LocalDate visitDate) {
        return new AttendanceLog(userId, visitDate);
    }
}
