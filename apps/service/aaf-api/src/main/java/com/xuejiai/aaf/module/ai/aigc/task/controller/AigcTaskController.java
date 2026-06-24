package com.xuejiai.aaf.module.ai.aigc.task.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.protection.RateLimit;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.license.FeatureRequired;
import com.xuejiai.aaf.framework.security.license.LicenseFeature;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskEventService;
import com.xuejiai.aaf.module.ai.aigc.task.service.AigcTaskService;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskPageDTO;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;
import com.xuejiai.aaf.module.ai.aigc.task.vo.ImageTaskRequest;
import com.xuejiai.aaf.module.ai.aigc.task.vo.VideoTaskRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AIGC 统一任务接口——提交生成任务、订阅实时事件、查询任务列表。
 *
 * @author AaronZZH
 */
@FeatureRequired(LicenseFeature.Codes.AIGC)
@Tag(name = "AIGC 统一任务")
@Slf4j
@RestController
@RequestMapping("/api/aigc/tasks")
@RequiredArgsConstructor
public class AigcTaskController
        extends BaseCrudController<AigcTask, AigcTaskVO, Void, Void, AigcTaskPageDTO> {

    private final AigcTaskService taskService;
    private final AigcTaskEventService eventService;
    private final OperatorContext operatorContext;
    private final com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository taskRepository;

    @Override
    protected BaseCrudService<AigcTask, AigcTaskVO, Void, Void, AigcTaskPageDTO> getService() {
        return taskService;
    }

    // BE-8 数据隔离：override 单条查询，加 ownership 校验（跨用户返回 404 防探测）
    @Override
    @Operation(summary = "查询任务详情（含 ownership 校验）")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<AigcTaskVO> get(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestParam(required = false) String queryToken,
            @RequestParam(defaultValue = "detail") String fieldSet) {
        return Result.success(taskService.getByIdOwned(id));
    }

    /** 屏蔽创建——任务通过 /submit 提交 */
    @Override
    public Result<AigcTaskVO> create(
            @org.springframework.web.bind.annotation.RequestBody Void body) {
        throw new BusinessException(GlobalErrorCode.METHOD_NOT_ALLOWED, "请使用 /submit 提交任务");
    }

    /** 屏蔽更新——任务不支持编辑 */
    @Override
    public Result<AigcTaskVO> update(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody Void body) {
        throw new BusinessException(GlobalErrorCode.METHOD_NOT_ALLOWED, "任务不支持编辑");
    }

    /** 提交任务请求 DTO */
    public record SubmitTaskDTO(
            @NotBlank String type,
            @NotBlank String prompt,
            /** 用于展示/命名的用户原始输入（不含项目提示词前缀），为空时回退到 prompt */
            String displayPrompt,
            String model,
            /** 所属项目 ID，null 表示全局任务 */
            Long projectId,
            /** 技能专属系统提示词（来自 SkillDefinition），执行时注入到 prompt 语境 */
            String systemPrompt,
            Map<String, Object> params) {}

    /** 预估积分消耗——不提交任务，仅返回积分数。 */
    @Operation(summary = "预估 AIGC 任务积分消耗")
    @PostMapping("/estimate")
    public Result<Long> estimate(@RequestBody SubmitTaskDTO dto) {
        Long userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        try {
            long credits =
                    taskService.estimateCredits(
                            userId,
                            dto.type(),
                            dto.model(),
                            dto.params() != null ? dto.params() : java.util.Map.of());
            return Result.success(credits);
        } catch (Exception e) {
            // 模型未配置、路由失败或参数不足时返回 null，前端显示"费用以后台为准"
            log.debug(
                    "[积分预估] 预估失败（忽略）: type={}, model={}, msg={}",
                    dto.type(),
                    dto.model(),
                    e.getMessage());
            return Result.success(null);
        }
    }

    /**
     * 提交生成任务（IMAGE / VIDEO / MODEL_3D）。
     *
     * @param dto 提交参数
     * @return 统一任务 ID
     */
    @Operation(summary = "提交 AIGC 生成任务")
    @PostMapping("/submit")
    @RateLimit(limit = 10, windowSeconds = 60, message = "任务提交过于频繁，请稍后再试")
    public Result<Long> submit(@Valid @RequestBody SubmitTaskDTO dto) {
        Long userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        Long taskId =
                switch (dto.type().toUpperCase()) {
                    case "IMAGE", "IMAGE_GEN" -> {
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
                    case "VIDEO", "VIDEO_GEN" -> {
                        var p = dto.params() != null ? dto.params() : Map.of();
                        yield taskService.submitVideoTask(
                                userId,
                                new VideoTaskRequest(
                                        dto.prompt(),
                                        dto.model(),
                                        dto.projectId(),
                                        toString(p.get("resolution")),
                                        toInt(p.get("duration")),
                                        toString(p.get("ratio")),
                                        toInt(p.get("seed")),
                                        toString(p.get("imageMode")),
                                        toString(p.get("imageUrl")),
                                        toStringList(p.get("referenceImageUrls")),
                                        toStringList(p.get("referenceVideoUrls")),
                                        toStringList(p.get("referenceAudioUrls")),
                                        toString(p.get("audioSetting")),
                                        toBoolean(p.get("promptExtend")),
                                        toBoolean(p.get("generateAudio"))));
                    }
                    case "MODEL_3D" -> {
                        var p = dto.params() != null ? dto.params() : Map.of();
                        yield taskService.submit3dTask(
                                userId,
                                dto.prompt(),
                                dto.model(),
                                toString(p.get("source")),
                                toString(p.get("textureQuality")),
                                dto.projectId());
                    }
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
                    case "VOICE" -> {
                        var p = dto.params() != null ? dto.params() : Map.of();
                        yield taskService.submitVoiceTask(
                                userId,
                                dto.prompt(),
                                toString(p.get("voice")),
                                dto.model(),
                                dto.projectId());
                    }
                    case "IMAGE_PROCESS" -> {
                        var p = dto.params() != null ? dto.params() : Map.of();
                        // imageUrl 优先取 params.imageUrl，其次取 prompt（兼容直接传 URL 的场景）
                        String imageUrl = toString(p.get("imageUrl"));
                        if (imageUrl == null || imageUrl.isBlank()) {
                            imageUrl = dto.prompt();
                        }
                        String method = toString(p.get("method"));
                        if (method == null || method.isBlank()) {
                            method = "SEGMENT_HD_COMMON_IMAGE";
                        }
                        yield taskService.submitImageProcessTask(
                                userId, imageUrl, method, dto.projectId());
                    }
                    default ->
                            throw new BusinessException(
                                    GlobalErrorCode.BAD_REQUEST, "不支持的任务类型: " + dto.type());
                };
        // 技能 systemPrompt 回写（不影响任务提交本身）
        if (dto.systemPrompt() != null && !dto.systemPrompt().isBlank()) {
            taskService.setSystemPrompt(taskId, dto.systemPrompt());
        }
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

    /** 统计今日生成任务数。 */
    @Operation(summary = "今日生成任务数")
    @GetMapping("/today-count")
    public Result<Long> todayCount() {
        Long userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        var todayStart = java.time.LocalDate.now().atStartOfDay();
        return Result.success(taskRepository.countByUserIdAndCreateTimeAfter(userId, todayStart));
    }

    /** 查询我的任务列表（分页）。 */
    @Override
    @Operation(summary = "查询我的 AIGC 任务列表")
    public Result<PageResult<AigcTaskVO>> page(
            @org.springframework.validation.annotation.Validated AigcTaskPageDTO request) {
        Long userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        return Result.success(
                taskService.pageByUser(userId, request.getPageNo(), request.getPageSize()));
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

    private static Boolean toBoolean(Object val) {
        return toBool(val);
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object val) {
        if (val == null) return null;
        if (val instanceof List<?> list) {
            return list.stream().filter(e -> e != null).map(Object::toString).toList();
        }
        return null;
    }
}
