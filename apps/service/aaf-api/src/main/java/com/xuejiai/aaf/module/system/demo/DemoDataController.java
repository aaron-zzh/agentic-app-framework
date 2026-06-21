package com.xuejiai.aaf.module.system.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 演示模式接口：一键加载 / 清理演示数据。 仅在 aaf.demo.enabled=true 时注册。
 *
 * @author AaronZZH
 */
@Tag(name = "演示模式")
@RestController
@RequestMapping("/api/system/demo")
@ConditionalOnProperty("aaf.demo.enabled")
@RequiredArgsConstructor
public class DemoDataController {

    private final DemoDataService demoDataService;

    @Operation(summary = "加载演示数据")
    @PostMapping("/load")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> load() {
        demoDataService.load();
        return Result.success();
    }

    @Operation(summary = "清理演示数据")
    @DeleteMapping("/clean")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<Void> clean() {
        demoDataService.clean();
        return Result.success();
    }
}
