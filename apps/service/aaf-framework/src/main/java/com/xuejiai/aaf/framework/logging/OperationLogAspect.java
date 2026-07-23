package com.xuejiai.aaf.framework.logging;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.xuejiai.aaf.common.util.ServletUtils;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 操作日志 AOP 切面，拦截 @OperationLog 注解方法并异步记录。 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final SafeLogValueResolver VALUE_RESOLVER = new SafeLogValueResolver();

    private final OperatorContext operatorContext;
    private final ApplicationEventPublisher eventPublisher;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog)
            throws Throwable {
        var start = System.currentTimeMillis();
        Object result = null;
        String errorMsg = null;
        boolean success = true;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            success = false;
            errorMsg = ex.getMessage();
            throw ex;
        } finally {
            var duration = System.currentTimeMillis() - start;
            try {
                publishEvent(joinPoint, operationLog, result, success, errorMsg, duration);
            } catch (Exception ex) {
                log.warn("记录操作日志失败", ex);
            }
        }
    }

    private void publishEvent(
            ProceedingJoinPoint joinPoint,
            OperationLog annotation,
            Object result,
            boolean success,
            String errorMsg,
            long duration) {
        var signature = (MethodSignature) joinPoint.getSignature();
        var method = signature.getMethod();
        var variables =
                VALUE_RESOLVER.createVariables(
                        method,
                        joinPoint.getArgs(),
                        joinPoint.getTarget().getClass(),
                        result,
                        errorMsg);
        var description = resolveTemplate(annotation.description(), variables);
        var bizNo = resolveTemplate(annotation.bizNo(), variables);

        String requestMethod = null;
        String requestUrl = null;
        String ip = null;
        String userAgent = null;
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            var request = sra.getRequest();
            requestMethod = request.getMethod();
            requestUrl = request.getRequestURI();
            ip = ServletUtils.getClientIp(request);
            userAgent = ServletUtils.getUserAgent(request);
        }

        var params = truncate(Arrays.toString(joinPoint.getArgs()), 2000);
        var responseStr = result != null ? truncate(result.toString(), 2000) : null;

        var event =
                new OperationLogEvent(
                        operatorContext.currentUserId().orElse(null),
                        null, // username 由持久化层补充
                        annotation.module(),
                        annotation.type().name(),
                        description,
                        bizNo,
                        requestMethod,
                        requestUrl,
                        params,
                        responseStr,
                        ip,
                        userAgent,
                        duration,
                        success,
                        errorMsg != null ? truncate(errorMsg, 500) : null,
                        LocalDateTime.now());

        eventPublisher.publishEvent(event);
    }

    private String resolveTemplate(String template, Map<String, Object> variables) {
        if (template == null || template.isBlank() || !template.contains("#{")) {
            return template;
        }
        var resolved = template;
        while (resolved.contains("#{")) {
            var startIndex = resolved.indexOf("#{");
            var endIndex = resolved.indexOf("}", startIndex);
            if (endIndex == -1) {
                throw new IllegalArgumentException("操作日志模板缺少右花括号: " + template);
            }
            var path = resolved.substring(startIndex + 2, endIndex);
            var value = VALUE_RESOLVER.resolve(path, variables);
            resolved =
                    resolved.substring(0, startIndex)
                            + (value == null ? "" : value)
                            + resolved.substring(endIndex + 1);
        }
        return resolved;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
