package com.xuejiai.aaf.module.system.tool;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.engine.tool.generator.ToolBlueprint;
import com.xuejiai.aaf.framework.engine.tool.generator.ToolGenerator;
import com.xuejiai.aaf.framework.security.ActorContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** 工具管理与调用接口。 */
@Tag(name = "工具管理")
@RestController
@RequestMapping("/api/system/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;
    private final ToolGenerator toolGenerator;
    private final ActorContext actorContext;

    @Operation(summary = "查询已注册工具列表", description = "可按来源过滤：LOCAL/MCP/CUSTOM")
    @GetMapping
    public Result<List<ToolVO>> list(@RequestParam(required = false) String source) {
        return Result.success(toolService.list(source));
    }

    @Operation(summary = "按 Role 查询可用工具", description = "返回该 Role 白名单内的工具")
    @GetMapping("/by-role/{roleId}")
    public Result<List<ToolVO>> listByRole(@PathVariable String roleId) {
        return Result.success(toolService.listByRole(roleId));
    }

    @Operation(summary = "调用工具", description = "统一工具调用入口，Agent/用户/外部系统均可调用")
    @PostMapping("/{toolName}/invoke")
    public Result<ToolCallResult> invoke(
            @PathVariable String toolName,
            @RequestBody @Valid ToolInvokeRequest request) {
        return Result.success(toolService.invoke(toolName, request.arguments()));
    }

    @Operation(summary = "授权工具调用", description = "用户确认后授予临时权限（本次会话有效）")
    @PostMapping("/{toolName}/approve")
    public Result<Void> approve(
            @PathVariable String toolName,
            @RequestParam String sessionId) {
        toolService.approve(sessionId, toolName);
        return Result.success();
    }

    /** 工具调用请求 */
    public record ToolInvokeRequest(@NotBlank String arguments) {}

    @Operation(summary = "AI 生成工具", description = "根据自然语言描述生成工具蓝图（需确认后注册）")
    @PostMapping("/generate")
    public Result<String> generate(@RequestBody @Valid ToolGenerateRequest request) {
        var result = toolGenerator.generateTool(request.description());
        return Result.success(result);
    }

    @Operation(summary = "确认并注册生成的工具")
    @PostMapping("/generate/confirm")
    public Result<Void> confirmGenerate(@RequestBody @Valid ToolBlueprint blueprint) {
        toolGenerator.confirmAndRegister(blueprint);
        return Result.success();
    }

    @Operation(summary = "查看工具源码")
    @GetMapping("/{toolName}/source")
    public Result<String> viewSource(@PathVariable String toolName) {
        return Result.success(toolGenerator.viewSource(toolName));
    }

    @Operation(summary = "标记工具为共享", description = "创建者或管理员可将私有工具共享，共享后所有人可见源码和使用")
    @PostMapping("/{toolName}/share")
    public Result<Void> share(@PathVariable String toolName) {
        toolGenerator.share(toolName);
        return Result.success();
    }

    public record ToolGenerateRequest(@NotBlank String description) {}
}
