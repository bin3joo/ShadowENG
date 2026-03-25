package com.bremenband.shadowengapi.domain.study.facade;

import com.bremenband.shadowengapi.domain.report.service.ReportService;
import com.bremenband.shadowengapi.domain.study.service.StudySessionWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 학습 사이클 완료 시 레포트 자동 생성을 조율하는 Facade.
 * study 도메인과 report 도메인 간의 의존성을 이 클래스에서 관리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudyReportFacade {

    private final StudySessionWriter studySessionWriter;
    private final ReportService      reportService;

    /**
     * step 4 완료 시 호출.
     * 세션 내 모든 문장이 학습 완료되면 cycleCount를 증가시키고 레포트를 자동 생성한다.
     */
    public void completeSessionAndAutoReport(Long sessionId, Long sentenceId) {
        int cycleNumber = studySessionWriter.completeSessionIfAllEvaluated(sessionId, sentenceId);
        if (cycleNumber > 0) {
            log.info("[StudyReportFacade] cycle {} completed, auto-generating report: sessionId={}", cycleNumber, sessionId);
            reportService.createReportInternal(sessionId, cycleNumber);
            log.info("[StudyReportFacade] report auto-generated: sessionId={}, cycleNumber={}", sessionId, cycleNumber);
        }
    }
}
