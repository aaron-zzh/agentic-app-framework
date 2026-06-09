package com.xuejiai.aaf.module.ai.aigc.task;

import java.util.Map;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;

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
            String model,
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
                                () ->
                                        new com.xuejiai.aaf.common.exception.BusinessException(
                                                com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                        .UNAUTHORIZED,
                                                "未登录"));
        Long taskId =
                switch (dto.type().toUpperCase()) {
                    case "IMAGE" -> {
                        Integer width =
                                dto.params() != null ? toInt(dto.params().get("width")) : null;
                        Integer height =
                                dto.params() != null ? toInt(dto.params().get("height")) : null;
                        yield taskService.submitImageTask(
                                userId, dto.prompt(), dto.model(), width, height);
                    }
                    case "VIDEO" -> taskService.submitVideoTask(userId, dto.prompt(), dto.model());
                    case "MODEL_3D" -> taskService.submit3dTask(userId, dto.prompt(), dto.model());
                    default ->
                            throw new com.xuejiai.aaf.common.exception.BusinessException(
                                    com.xuejiai.aaf.common.exception.GlobalErrorCode.BAD_REQUEST,
                                    "不支持的任务类型: " + dto.type());
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
                                () ->
                                        new com.xuejiai.aaf.common.exception.BusinessException(
                                                com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                        .UNAUTHORIZED,
                                                "未登录"));
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
                                () ->
                                        new com.xuejiai.aaf.common.exception.BusinessException(
                                                com.xuejiai.aaf.common.exception.GlobalErrorCode
                                                        .UNAUTHORIZED,
                                                "未登录"));
        return Result.success(taskService.pageByUser(userId, pageNo, pageSize));
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
}
