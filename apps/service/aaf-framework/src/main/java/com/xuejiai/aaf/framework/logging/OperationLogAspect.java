package com.xuejiai.aaf.framework.logging;

import java.time.LocalDateTime;
import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.xuejiai.aaf.common.util.ServletUtils;
import com.xuejiai.aaf.framework.security.ActorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 操作日志 AOP 切面，拦截 @OperationLog 注解方法并异步记录。 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final ActorContext actorContext;
    private final ApplicationEventPublisher eventPublisher;

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer DISCOVERER =
            new DefaultParameterNameDiscoverer();

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
        var method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        var ctx =
                new MethodBasedEvaluationContext(
                        joinPoint.getTarget(), method, joinPoint.getArgs(), DISCOVERER);
        // 将返回值放入 SpEL 上下文
        ctx.setVariable("result", result);

        var description = resolveSpel(annotation.description(), ctx);
        var bizNo = resolveSpel(annotation.bizNo(), ctx);

        // 请求信息
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

        // 请求参数（截断避免过大）
        var params = truncate(Arrays.toString(joinPoint.getArgs()), 2000);
        var responseStr = result != null ? truncate(result.toString(), 2000) : null;

        var event =
                new OperationLogEvent(
                        actorContext.currentUserId().orElse(null),
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

    private String resolveSpel(String template, MethodBasedEvaluationContext ctx) {
        if (template == null || template.isBlank()) {
            return template;
        }
        // 支持 #{} 包裹的 SpEL 表达式
        if (!template.contains("#{")) {
            return template;
        }
        try {
            // 提取 #{...} 中的表达式并替换
            var result = template;
            while (result.contains("#{")) {
                var startIdx = result.indexOf("#{");
                var endIdx = result.indexOf("}", startIdx);
                if (endIdx == -1) break;
                var expr = result.substring(startIdx + 2, endIdx);
                var value = PARSER.parseExpression(expr).getValue(ctx, String.class);
                result =
                        result.substring(0, startIdx)
                                + (value != null ? value : "")
                                + result.substring(endIdx + 1);
            }
            return result;
        } catch (Exception ex) {
            log.debug("SpEL 解析失败: {}", template, ex);
            return template;
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
