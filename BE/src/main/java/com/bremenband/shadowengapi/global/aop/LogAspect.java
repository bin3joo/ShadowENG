package com.bremenband.shadowengapi.global.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Aspect
@Component
public class LogAspect {

    // com.bremenband.shadowengapi 패키지 및 하위 패키지의 모든 Controller 메서드 대상
    @Around("execution(* com.bremenband.shadowengapi..*Controller.*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // 실제 타겟 메서드 실행
        Object result = joinPoint.proceed();

        stopWatch.stop();

        long totalTimeMillis = stopWatch.getTotalTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // 실행 시간이 1초(1000ms)를 초과하면 WARN 레벨로 로깅
        if (totalTimeMillis > 1000) {
            log.warn("[SLOW API] {}.{} took {} ms", className, methodName, totalTimeMillis);
        } else {
            log.info("[API] {}.{} took {} ms", className, methodName, totalTimeMillis);
        }

        return result;
    }
}
