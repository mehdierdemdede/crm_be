package com.leadsyncpro.audit;

import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Aspect
@Component
public class PiiAccessAuditAspect {

    @Around("@annotation(piiAccess)")
    public Object auditPiiAccess(ProceedingJoinPoint joinPoint, PiiAccess piiAccess) throws Throwable {
        String username = resolveUsername();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String operation = determineOperation(piiAccess, signature);
        Instant timestamp = Instant.now();
        log.info(
                "PII_ACCESS user={} operation={} when={}",
                username,
                operation,
                timestamp);
        return joinPoint.proceed();
    }

    private String resolveUsername() {
        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName)
                .filter(StringUtils::hasText)
                .filter(name -> !"anonymousUser".equalsIgnoreCase(name))
                .orElse("anonymous");
    }

    private String determineOperation(PiiAccess piiAccess, MethodSignature signature) {
        if (piiAccess != null && StringUtils.hasText(piiAccess.value())) {
            return piiAccess.value();
        }
        return signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName();
    }
}
