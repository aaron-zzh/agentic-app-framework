package com.xuejiai.aaf.config;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.license.LicenseRequiredException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/** 全局异常处理器，将异常统一转为 Result 响应。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 乐观锁冲突（数据已被其他人修改） */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<?> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        log.info("乐观锁冲突: {}", e.getMessage());
        return Result.error(409, "数据已被修改，请刷新后重试");
    }

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.info("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
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

    /** 兜底：未知异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("未知异常: uri={}", request.getRequestURI(), e);
        return Result.error(GlobalErrorCode.INTERNAL_SERVER_ERROR);
    }
}
