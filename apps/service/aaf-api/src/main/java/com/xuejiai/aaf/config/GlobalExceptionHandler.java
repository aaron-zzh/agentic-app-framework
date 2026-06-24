package com.xuejiai.aaf.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.protection.RateLimitExceededException;
import com.xuejiai.aaf.framework.security.license.LicenseRequiredException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/** 全局异常处理器，将异常统一转为 Result 响应。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 权限不足 */
    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleAccessDenied(
            AuthorizationDeniedException e, HttpServletRequest request) {
        var auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        String principal = auth == null ? "<null>" : String.valueOf(auth.getName());
        String authorities = auth == null ? "<null>" : String.valueOf(auth.getAuthorities());
        String jwtRoles = "<not-jwt>";
        if (auth
                instanceof
                org.springframework.security.oauth2.server.resource.authentication
                                .JwtAuthenticationToken
                        jwtAuth) {
            jwtRoles = String.valueOf(jwtAuth.getToken().getClaimAsStringList("roles"));
        }
        log.warn(
                "[AccessDenied] {} {} principal={} authorities={} jwtRoles={} reason={}",
                request.getMethod(),
                request.getRequestURI(),
                principal,
                authorities,
                jwtRoles,
                e.getMessage());
        return Result.error(GlobalErrorCode.FORBIDDEN);
    }

    /** 限流触发 */
    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<?> handleRateLimit(RateLimitExceededException e) {
        log.info("限流触发: {}", e.getMessage());
        return Result.error(429, e.getMessage());
    }

    // 乐观锁冲突处理（暂未启用 @Version，预留供未来手动实现乐观锁时使用）
    // @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    // @ResponseStatus(HttpStatus.CONFLICT)
    // public Result<?> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
    //     log.info("乐观锁冲突: {}", e.getMessage());
    //     return Result.error(409, "数据已被修改，请刷新后重试");
    // }

    /** 积分余额不足 */
    @ExceptionHandler(InsufficientCreditsException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public Result<?> handleInsufficientCredits(InsufficientCreditsException e) {
        long shortBy = Math.max(0, e.getEstimatedCost() - e.getBalance() - e.getOverdraft());
        log.info(
                "积分余额不足: userId={}, balance={}, estimatedCost={}, overdraft={}, shortBy={}",
                e.getUserId(),
                e.getBalance(),
                e.getEstimatedCost(),
                e.getOverdraft(),
                shortBy);
        return Result.error(402, "积分余额不足，请充值后继续使用");
    }

    /** 业务异常——根据业务码动态映射 HTTP 状态码 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        log.info("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus httpStatus =
                switch (e.getCode()) {
                    case 400 -> HttpStatus.BAD_REQUEST;
                    case 401 -> HttpStatus.UNAUTHORIZED;
                    case 403 -> HttpStatus.FORBIDDEN;
                    case 404 -> HttpStatus.NOT_FOUND;
                    case 409 -> HttpStatus.CONFLICT;
                    default -> HttpStatus.BAD_REQUEST;
                };
        return ResponseEntity.status(httpStatus).body(Result.error(e.getCode(), e.getMessage()));
    }

    /** 商业授权异常 */
    @ExceptionHandler(LicenseRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleLicenseRequired(LicenseRequiredException e) {
        log.info("商业授权拦截: feature={}, upgradeUrl={}", e.getFeatureName(), e.getUpgradeUrl());
        return Result.error(GlobalErrorCode.FORBIDDEN, e.getMessage());
    }

    /** 参数校验异常（@Valid 注解触发） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message =
                e.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .findFirst()
                        .orElse("参数校验失败");
        return Result.error(GlobalErrorCode.BAD_REQUEST, message);
    }

    /** 参数绑定异常 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e) {
        String message =
                e.getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .findFirst()
                        .orElse("参数绑定失败");
        return Result.error(GlobalErrorCode.BAD_REQUEST, message);
    }

    /** 约束违反异常 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        return Result.error(GlobalErrorCode.BAD_REQUEST, e.getMessage());
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<?> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return Result.error(GlobalErrorCode.METHOD_NOT_ALLOWED);
    }

    /** 资源不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNotFound(NoResourceFoundException e) {
        return Result.error(GlobalErrorCode.NOT_FOUND);
    }

    /** SSE/异步请求超时——正常生命周期，不记录 error */
    @ExceptionHandler(
            org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public void handleAsyncTimeout() {
        // SSE 连接超时是正常行为，前端会自动重连，无需记录日志
    }

    /** 客户端主动断开 SSE 连接——正常行为，不记录 error */
    @ExceptionHandler(
            org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public void handleAsyncNotUsable() {
        // 浏览器刷新/关闭 tab 时 SSE 连接断开，属正常生命周期
    }

    /** 文件下载等响应已设置非 JSON Content-Type 时，无法再写 JSON body */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotWritableException.class)
    public ResponseEntity<Void> handleMessageNotWritable(
            org.springframework.http.converter.HttpMessageNotWritableException e,
            HttpServletRequest request) {
        log.warn(
                "响应写出失败（Content-Type 不兼容）: uri={}, err={}",
                request.getRequestURI(),
                e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /** 兜底：未知异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e, HttpServletRequest request) {
        log.error("未知异常: uri={}", request.getRequestURI(), e);
        // SSE 请求已设置 text/event-stream，无法再写 JSON body，直接返回 503 空响应避免 converter 错误
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(GlobalErrorCode.INTERNAL_SERVER_ERROR));
    }
}
