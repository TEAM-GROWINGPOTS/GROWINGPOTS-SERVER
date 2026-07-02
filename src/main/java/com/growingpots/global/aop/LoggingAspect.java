package com.growingpots.global.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logRequest(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();

        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();
        String handler = pjp.getSignature().toShortString();
        long start = System.currentTimeMillis();

        try {
            Object result = pjp.proceed();
            log.info("[OK] [{}] {} | {} | {}ms", httpMethod, uri, handler, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.info("[FAIL] [{}] {} | {} | {}ms | exception={}", httpMethod, uri, handler,
                    System.currentTimeMillis() - start, e.getClass().getSimpleName());
            throw e;
        }
    }
}
