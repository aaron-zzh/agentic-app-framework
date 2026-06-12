package com.xuejiai.aaf.module.ai.aigc.task.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskEventService;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;
import com.xuejiai.aaf.module.ai.aigc.task.vo.ImageTaskRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * AIGC 统一任务接口——提交生成任务、订阅实时事件、查询任务列表。
 *
 * @author Kiro
 */
@Tag(name = "AIGC 统一任务")
@RestController
@RequestMapping("/api/aigc/tasks")
@RequiredArgsConstructor
public class AigcTaskController {

    private final AigcTaskService taskService;
    private final AigcTaskEventService eventService;
    private final OperatorContext operatorContext;

    /** 提交任务请求 DTO */
    public record SubmitTaskDTO(
            @NotBlank String type,
            @NotBlank String prompt,
            /** 用于展示/命名的用户原始输入（不含项目提示词前缀），为空时回退到 prompt */
            String displayPrompt,
            String model,
            /** 所属项目 ID，null 表示全局任务 */
            Long projectId,
            Map<String, Object> params) {}

    /**
     * 提交生成任务（IMAGE / VIDEO / MODEL_3D）。
     *
     * @param dto 提交参数
     * @return 统一任务 ID
     */
    @Operation(summary = "提交 AIGC 生成任务")
    @PostMapping("/submit")
    public Result<Long> submit(@Valid @RequestBody SubmitTaskDTO dto) {
        Long userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        Long taskId =
                switch (dto.type().toUpperCase()) {
                    case "IMAGE" -> {
                        var p = dto.params() != null ? dto.params() : Map.of();
                        String imageUrl = toString(p.get("imageUrl"));
                        @SuppressWarnings("unchecked")
                        List<String> imageUrls =
                                p.get("imageUrls") instanceof List
                                        ? (List<String>) p.get("imageUrls")
                                        : (imageUrl != null ? List.of(imageUrl) : null);
                        int w = toInt(p.get("width")) != null ? toInt(p.get("width")) : 1024;
                        int h = toInt(p.get("height")) != null ? toInt(p.get("height")) : 1024;
                        yield taskService.submitImageTask(
                                userId,
                                new ImageTaskRequest(
                                        dto.prompt(),
                                        dto.model(),
                                        w,
                                        h,
                                        toString(p.get("negativePrompt")),
                                        toInt(p.get("seed")),
                                        toBool(p.get("promptExtend")),
                                        toInt(p.get("imageCount")),
                                        imageUrls,
                                        toString(p.get("quality")),
                                        toString(p.get("format")),
                                        toString(p.get("background")),
                                        toString(p.get("contentModeration")),
                                        toString(p.get("sizePreset")),
                                        toString(p.get("aspectRatio")),
                                        dto.displayPrompt(),
                                        null,
                                        null,
                                        dto.projectId()));
                    }
                    case "VIDEO" ->
                            taskService.submitVideoTask(
                                    userId, dto.prompt(), dto.model(), dto.projectId());
                    case "MODEL_3D" ->
                            taskService.submit3dTask(
                                    userId, dto.prompt(), dto.model(), dto.projectId());
                    case "MUSIC" -> {
                        var p = dto.params() != null ? dto.params() : Map.of();
                        yield taskService.submitMusicTask(
                                userId,
                                dto.prompt(),
                                dto.model(),
                                toString(p.get("lyrics")),
                                toString(p.get("gender")),
                                dto.projectId());
                    }
                    default ->
                            throw new BusinessException(
                                    GlobalErrorCode.BAD_REQUEST, "不支持的任务类型: " + dto.type());
                };
        return Result.success(taskId);
    }

    /**
     * SSE 订阅当前用户的所有 AIGC 任务事件。
     *
     * @return SSE 事件流
     */
    @Operation(summary = "SSE 订阅 AIGC 任务事件")
    @GetMapping("/stream")
    public SseEmitter stream() {
        Long userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        return eventService.subscribe(userId);
    }

    /**
     * 查询我的任务列表（分页）。
     *
     * @param pageNo 页码（默认 1）
     * @param pageSize 每页大小（默认 20）
     * @return 分页任务列表
     */
    @Operation(summary = "查询我的 AIGC 任务列表")
    @GetMapping
    public Result<PageResult<AigcTaskVO>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        return Result.success(taskService.pageByUser(userId, pageNo, pageSize));
    }

    @Operation(summary = "查询单个 AIGC 任务")
    @GetMapping("/{id}")
    public Result<AigcTaskVO> getById(@PathVariable Long id) {
        return Result.success(taskService.getById(id));
    }

    @Operation(summary = "删除 AIGC 任务")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.success();
    }

    private static Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String toString(Object val) {
        return val == null ? null : val.toString();
    }

    private static Boolean toBool(Object val) {
        if (val == null) return null;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(val.toString());
    }
}
