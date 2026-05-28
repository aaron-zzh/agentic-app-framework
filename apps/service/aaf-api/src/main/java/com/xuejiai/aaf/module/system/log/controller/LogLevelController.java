package com.xuejiai.aaf.module.system.log.controller;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

import lombok.RequiredArgsConstructor;

/**
 * 日志级别动态管理接口（仅 admin 可访问）。
 *
 * @author AaronZZH & Kiro
 */
@RestController
@RequestMapping("/api/admin/log-levels")
@RequiredArgsConstructor
public class LogLevelController {

    private final LoggingSystem loggingSystem;

    /** 获取当前所有 logger 级别。 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, String>> getLevels() {
        var configs = loggingSystem.getLoggerConfigurations();
        var result =
                configs.stream()
                        .collect(
                                Collectors.toMap(
                                        c -> c.getName().isEmpty() ? "ROOT" : c.getName(),
                                        c -> c.getEffectiveLevel().name()));
        return Result.success(result);
    }

    /** 动态调整日志级别。 */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> setLevel(@RequestBody LogLevelDTO dto) {
        loggingSystem.setLogLevel(dto.loggerName(), LogLevel.valueOf(dto.level().toUpperCase()));
        return Result.success(null);
    }

    /** 日志级别调整请求。 */
    public record LogLevelDTO(String loggerName, String level) {}
}
