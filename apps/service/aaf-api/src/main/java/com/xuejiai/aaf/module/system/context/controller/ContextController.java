package com.xuejiai.aaf.module.system.context.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 页面上下文接口（AI 感知预留）。
 *
 * <p>当前为骨架实现，返回空数据。v0.2.0 随 AI 感知能力落地时补充完整逻辑：
 *
 * <ul>
 *   <li>page-enter：记录用户当前所在页面及可用组件，供 AI 感知上下文使用
 *   <li>chatter-config：持久化用户每个页面的 Chatter 配置（preset/agentRole/open）
 * </ul>
 *
 * @author AaronZZH & Kiro
 */
@Tag(name = "页面上下文（AI 感知预留）")
@RestController
@RequestMapping("/api/context")
public class ContextController {

    // ========== 请求/响应 DTO ==========

    /** 页面进入事件 */
    public record PageEnterRequest(
            @NotBlank String pageId,
            String pageTitle,
            /** 当前页面可用的可拖放组件列表（供 AI 感知） */
            java.util.List<String> availableComponents) {}

    /** Chatter 配置 */
    public record ChatterConfigVO(String preset, String agentRole, boolean open, String layout) {}

    /** Chatter 配置更新请求 */
    public record ChatterConfigUpdateRequest(
            @NotBlank String pageId,
            String preset,
            String agentRole,
            Boolean open,
            String layout) {}

    // ========== 端点 ==========

    @Operation(summary = "页面进入事件（AI 感知预留，当前为空实现）")
    @PostMapping("/page-enter")
    public Result<Void> pageEnter(@RequestBody @Valid PageEnterRequest request) {
        // TODO v0.2.0：记录用户当前页面上下文到 AI 感知服务
        // aiAwarenessService.onPageEnter(userId, request);
        return Result.success(null);
    }

    @Operation(summary = "获取页面 Chatter 配置（本地无缓存时调用）")
    @GetMapping("/chatter-config")
    public Result<ChatterConfigVO> getChatterConfig(@RequestParam String pageId) {
        // TODO v0.2.0：从 user_preference 表按 (userId, pageId) 查询
        // 当前返回 null，前端降级到默认配置
        return Result.success(null);
    }

    @Operation(summary = "保存页面 Chatter 配置")
    @PutMapping("/chatter-config")
    public Result<Void> saveChatterConfig(@RequestBody @Valid ChatterConfigUpdateRequest request) {
        // TODO v0.2.0：写入 user_preference 表
        // userPreferenceService.save(userId, "chatter:" + request.pageId(), request);
        return Result.success(null);
    }
}
