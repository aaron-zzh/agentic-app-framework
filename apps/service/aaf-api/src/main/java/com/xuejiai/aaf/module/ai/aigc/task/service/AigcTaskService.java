package com.xuejiai.aaf.module.ai.aigc.task.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.enums.aigc.AigcTaskStatusEnum;
import com.xuejiai.aaf.common.enums.aigc.AigcTaskTypeEnum;
import com.xuejiai.aaf.common.enums.pay.CreditTransactionCategoryEnum;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.model3d.Model3dGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.ai.video.VideoGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoRequest;
import com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoTaskResult;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.system.config.service.SystemConfigService;
import com.xuejiai.aaf.module.ai.aigc.ErrorCodeConstants;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcGeneratedMediaCommand;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaType;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaView;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.event.AigcTaskTerminalEvent;
import com.xuejiai.aaf.module.ai.aigc.task.mapper.AigcTaskMapper;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskPageDTO;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;
import com.xuejiai.aaf.module.ai.aigc.task.vo.ImageTaskRequest;
import com.xuejiai.aaf.module.ai.aigc.task.vo.VideoTaskRequest;
import com.xuejiai.aaf.module.system.file.api.FileReference;
import com.xuejiai.aaf.module.system.file.api.FileStoragePort;
import com.xuejiai.aaf.module.system.file.api.StoredFile;
import com.xuejiai.aaf.module.user.growth.event.UserGrowthEvent;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * AIGC 统一任务服务——汇聚图像/视频/3D 模型/音乐四类生成任务，统一管理状态流转和 OSS 存储。
 *
 * @author AaronZZH
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcTaskService
        extends BaseCrudService<AigcTask, AigcTaskVO, Void, Void, AigcTaskPageDTO> {

    /** 阿里云通用高清分割计费单价（元/次），0.007 元。 */
    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "type",
                    "status",
                    "provider",
                    "model",
                    "modelName",
                    "projectId",
                    "createTime",
                    "updateTime");

    private static final double IMAGE_PROCESS_COMMON_PRICE_YUAN = 0.007;

    /** 阿里云高清人体分割计费单价（元/次），0.007 元。 */
    private static final double IMAGE_PROCESS_HD_BODY_PRICE_YUAN = 0.007;

    /** 配音文本最大长度（字） */
    private static final int VOICE_TEXT_MAX_LEN = 200;

    private static final String EVENT_CREATED = "task.created";
    private static final String EVENT_COMPLETED = "task.completed";
    private static final String EVENT_FAILED = "task.failed";

    private final AigcTaskRepository taskRepo;
    private final AigcTaskEventService eventService;
    private final AigcTaskMapper taskMapper;
    private final FileStoragePort fileService;
    private final AigcMediaApi mediaApi;
    private final CapabilityRouter capabilityRouter;
    private final AigcTaskExecutor taskExecutor;
    private final AiCreditGuard creditGuard;
    private final SystemConfigService systemConfigService;
    private final ConfigCacheManager configCacheManager;
    private final AiServiceRegistry aiServiceRegistry;
    private final Model3dGenerationService model3dGenerationService;
    private final ApplicationEventPublisher eventPublisher;

    // ========== BaseCrudService 必须实现 ==========

    @Override
    protected AigcTaskRepository getRepository() {
        return taskRepo;
    }

    @Override
    public AigcTaskVO toVO(AigcTask task) {
        return taskMapper.toVO(task);
    }

    /** 创建入口由业务方法（submit*）负责，不支持通用 create。 */
    @Override
    protected AigcTask toEntity(Void createDTO) {
        throw new UnsupportedOperationException("请使用 submit*Task 方法创建任务");
    }

    /** 任务状态由内部流转，不支持通用 update。 */
    @Override
    protected void updateEntity(AigcTask entity, Void updateDTO) {
        throw new UnsupportedOperationException("任务状态由内部流转管理");
    }

    @Override
    protected Specification<AigcTask> buildSpec(AigcTaskPageDTO dto) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (dto.getUserId() != null)
                predicates.add(cb.equal(root.get("userId"), dto.getUserId()));
            if (dto.getType() != null) predicates.add(cb.equal(root.get("type"), dto.getType()));
            if (dto.getStatus() != null)
                predicates.add(cb.equal(root.get("status"), dto.getStatus()));
            if (dto.getProjectId() != null)
                predicates.add(cb.equal(root.get("projectId"), dto.getProjectId()));
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional
    public Long submitImageTask(Long userId, ImageTaskRequest req) {
        if (req.imageFileIds() != null) {
            req.imageFileIds().forEach(fileService::requireCurrentOwner);
        }
        long t0 = System.currentTimeMillis();
        var ctx =
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_IMAGE_GEN, req.model());
        var resolvedAiModel = capabilityRouter.resolve(ctx);
        // 委托 AiCapability 默认估算逻辑（与装饰器 creditCall 保持一致）
        long estimatedCost =
                aiServiceRegistry
                        .get(ImageGenerationService.class, resolvedAiModel)
                        .estimateCost(resolvedAiModel, req, creditGuard.getMarkupRate());
        log.debug(
                "[submitImageTask] 估算积分: userId={}, model={}, modelPrice={}, quotaType={},"
                        + " imageCount={}, quality={}, markup={}, estimatedCost={}",
                userId,
                resolvedAiModel.getModelId(),
                resolvedAiModel.getModelPrice(),
                resolvedAiModel.getQuotaType(),
                req.imageCount(),
                req.quality(),
                creditGuard.getMarkupRate(),
                estimatedCost);
        creditGuard.precheck(
                userId, CreditTransactionCategoryEnum.IMAGE_GEN.getCode(), estimatedCost);
        log.debug("[submitImageTask] resolve 耗时: {}ms", System.currentTimeMillis() - t0);
        String resolvedModel = resolvedAiModel.getModelId();

        var task =
                buildTask(
                        userId,
                        AigcTaskTypeEnum.IMAGE.getCode(),
                        req.prompt(),
                        resolvedModel,
                        resolvedAiModel.getDisplayName(),
                        req.projectId());
        task.setParams(req.toParamsJson());
        taskRepo.save(task);
        retainImageTaskInputs(task.getId(), req.imageFileIds());
        eventService.push(userId, EVENT_CREATED, toVO(task));

        final Long taskId = task.getId();
        final String prompt = req.prompt();
        final String mockUrl = isMockEnabled() ? getMockValue("image") : null;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        taskExecutor.submitSync(taskId, prompt, resolvedModel, mockUrl);
                    }
                });
        log.debug(
                "[submitImageTask] 总耗时: {}ms, taskId={}",
                System.currentTimeMillis() - t0,
                task.getId());
        return task.getId();
    }

    @Transactional
    public Long submitVideoTask(Long userId, VideoTaskRequest req) {
        if (req.imageFileId() != null) {
            fileService.requireCurrentOwner(req.imageFileId());
        }
        if (req.referenceImageFileIds() != null) {
            req.referenceImageFileIds().forEach(fileService::requireCurrentOwner);
        }
        var ctx =
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_VIDEO_GEN, req.model());
        var resolvedModel = capabilityRouter.resolve(ctx);
        String resolvedModelId = resolvedModel.getModelId();

        // 构造 VideoRequest 用于 estimateCost precheck
        var imageModeEnum =
                req.imageMode() != null ? VideoRequest.ImageMode.valueOf(req.imageMode()) : null;
        var videoReq =
                new VideoRequest(
                        req.prompt(),
                        req.imageFileId() != null ? "internal-file" : null,
                        req.referenceImageFileIds() != null
                                ? req.referenceImageFileIds().stream()
                                        .map(ignored -> "internal-file")
                                        .toList()
                                : null,
                        resolvedModelId,
                        req.resolution(),
                        req.ratio(),
                        req.duration(),
                        req.seed(),
                        imageModeEnum);
        var svc = aiServiceRegistry.get(VideoGenerationService.class, resolvedModel);
        long estimatedCost = svc.estimateCost(resolvedModel, videoReq, creditGuard.getMarkupRate());
        creditGuard.precheck(userId, CreditTransactionCategoryEnum.VIDEO.getCode(), estimatedCost);

        var task =
                buildTask(
                        userId,
                        AigcTaskTypeEnum.VIDEO.getCode(),
                        req.prompt(),
                        resolvedModelId,
                        resolvedModel.getDisplayName(),
                        req.projectId());
        task.setParams(req.toParamsJson());

        taskRepo.save(task);
        retainVideoTaskInputs(task.getId(), req);
        eventService.push(userId, EVENT_CREATED, toVO(task));

        final Long taskId = task.getId();
        final String mockUrl = isMockEnabled() ? getMockValue("video") : null;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        taskExecutor.submitVideoAsync(
                                taskId, req.prompt(), resolvedModelId, mockUrl);
                    }
                });
        log.info("[submitVideoTask] 视频生成任务已创建: taskId={}, model={}", task.getId(), resolvedModelId);
        return task.getId();
    }

    /** AIGC 输入文件随任务保留。任务终态后仍保留该引用，使任务详情和结果具备可复现的输入审计记录。 */
    private void retainImageTaskInputs(Long taskId, java.util.List<Long> fileIds) {
        if (fileIds == null) return;
        for (int index = 0; index < fileIds.size(); index++) {
            fileService.retain(
                    fileIds.get(index),
                    new FileReference(
                            "AIGC_TASK", taskId, "imageFileIds[" + index + "]", "TASK_INPUT"));
        }
    }

    private void retainVideoTaskInputs(Long taskId, VideoTaskRequest request) {
        if (request.imageFileId() != null) {
            fileService.retain(
                    request.imageFileId(),
                    new FileReference("AIGC_TASK", taskId, "imageFileId", "TASK_INPUT"));
        }
        if (request.referenceImageFileIds() == null) return;
        for (int index = 0; index < request.referenceImageFileIds().size(); index++) {
            fileService.retain(
                    request.referenceImageFileIds().get(index),
                    new FileReference(
                            "AIGC_TASK",
                            taskId,
                            "referenceImageFileIds[" + index + "]",
                            "TASK_INPUT"));
        }
    }

    @Transactional
    public Long submit3dTask(
            Long userId,
            String prompt,
            String model,
            String source,
            String textureQuality,
            Long projectId) {
        // 路由模型
        var ctx = CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_MODEL_3D, model);
        var resolvedModel = capabilityRouter.resolve(ctx);
        String resolvedModelId = resolvedModel.getModelId();

        // 按 source + textureQuality 估算积分并预检
        var req = buildModel3dRequest(prompt, source, textureQuality);
        long estimatedCost =
                model3dGenerationService.estimateCost(
                        resolvedModel, req, creditGuard.getMarkupRate());
        creditGuard.precheck(
                userId, CreditTransactionCategoryEnum.MODEL_3D.getCode(), estimatedCost);

        var task =
                buildTask(
                        userId,
                        AigcTaskTypeEnum.MODEL_3D.getCode(),
                        prompt,
                        resolvedModelId,
                        resolvedModel.getDisplayName(),
                        projectId);
        // 存 source/textureQuality 供完成时结算
        var paramsMap = new java.util.HashMap<String, Object>();
        paramsMap.put("source", source != null ? source : "text");
        paramsMap.put("textureQuality", textureQuality != null ? textureQuality : "none");
        task.setParams(JsonUtils.toJsonString(paramsMap));
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));

        final Long taskId = task.getId();
        final String mockUrl3d = isMockEnabled() ? getMockValue("model3d") : null;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        taskExecutor.submitModel3dSync(taskId, prompt, mockUrl3d);
                    }
                });
        return task.getId();
    }

    /** 根据 source 构造对应请求对象，用于 estimateCost。 */
    private Object buildModel3dRequest(String prompt, String source, String textureQuality) {
        String tex = textureQuality != null ? textureQuality : "none";
        return switch (source != null ? source : "text") {
            case "image" -> new Model3dGenerationService.ImageTo3dRequest(null, tex, null);
            case "multi" -> new Model3dGenerationService.MultiImageTo3dRequest(null, tex, null);
            default -> new Model3dGenerationService.TextTo3dRequest(prompt, tex, null);
        };
    }

    @Transactional
    public Long submitMusicTask(
            Long userId,
            String prompt,
            String model,
            String lyrics,
            String gender,
            Long projectId) {
        var ctx =
                CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_MUSIC_GEN, model);
        var resolvedModel = capabilityRouter.resolve(ctx);
        var musicReq = new MusicGenerationService.MusicRequest(prompt, lyrics, gender, "mp3");
        long estimatedCost =
                aiServiceRegistry
                        .get(MusicGenerationService.class, resolvedModel)
                        .estimateCost(resolvedModel, musicReq, creditGuard.getMarkupRate());
        creditGuard.precheck(userId, CreditTransactionCategoryEnum.MUSIC.getCode(), estimatedCost);
        var task =
                buildTask(
                        userId,
                        AigcTaskTypeEnum.MUSIC.getCode(),
                        prompt,
                        resolvedModel.getModelId(),
                        null,
                        projectId);
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));

        final Long taskId = task.getId();
        final String resolvedGender = gender != null ? gender : "female";
        final String mockUrlMusic = isMockEnabled() ? getMockValue("audio") : null;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        taskExecutor.submitMusicSync(
                                taskId, prompt, lyrics, resolvedGender, mockUrlMusic);
                    }
                });
        return task.getId();
    }

    /**
     * 提交配音生成任务（TTS）。文本长度上限 {@value #VOICE_TEXT_MAX_LEN} 字。
     *
     * @param userId 用户 ID
     * @param text 配音文本（即 prompt）
     * @param voice 音色编码，null 时执行器回退到系统默认音色
     * @param model TTS 模型名，可空
     * @param projectId 所属项目 ID，可空
     * @return 任务 ID
     */
    @Transactional
    public Long submitVoiceTask(
            Long userId, String text, String voice, String model, Long projectId) {
        if (text == null || text.isBlank()) {
            throw exception(ErrorCodeConstants.AIGC_TASK_VOICE_TEXT_EMPTY);
        }
        if (text.length() > VOICE_TEXT_MAX_LEN) {
            throw exception(ErrorCodeConstants.AIGC_TASK_VOICE_TEXT_TOO_LONG, VOICE_TEXT_MAX_LEN);
        }

        // 通过 CapabilityRouter 解析模型（显式指定 → 用户偏好 → 系统默认）
        var ctx =
                CapabilityRoutingContext.of(userId, CapabilityRoutingContext.CAP_SPEECH_TTS, model);
        var resolvedModel = capabilityRouter.resolve(ctx);
        String resolvedModelId = resolvedModel.getModelId();

        // precheck：通过 estimateCost 精确预估（SpeechService 按字符数计算）
        long estimatedCost =
                aiServiceRegistry
                        .get(SpeechService.class, resolvedModel)
                        .estimateCost(resolvedModel, text, creditGuard.getMarkupRate());
        creditGuard.precheck(
                userId, CreditTransactionCategoryEnum.SPEECH_TTS.getCode(), estimatedCost);

        var task =
                buildTask(
                        userId,
                        AigcTaskTypeEnum.VOICE.getCode(),
                        text,
                        resolvedModelId,
                        resolvedModel.getDisplayName(),
                        projectId);
        if (voice != null && !voice.isBlank()) {
            task.setParams(JsonUtils.toJsonString(Map.of("voice", voice)));
        }
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));

        final Long taskId = task.getId();
        final String mockUrlVoice = isMockEnabled() ? getMockValue("audio") : null;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        taskExecutor.submitVoiceSync(taskId, text, voice, mockUrlVoice);
                    }
                });
        log.info(
                "[submitVoiceTask] 配音生成任务已创建: taskId={}, model={}, voice={}",
                task.getId(),
                resolvedModelId,
                voice);
        return task.getId();
    }

    /**
     * 提交图像处理任务（如人像高清抠图 SEGMENT_HD_BODY）。
     *
     * <p>积分按 {@code IMAGE_PROCESS} 分类预检。计费标准：阿里云高清人体分割 0.007 元/次， 乘以系统加价倍率（默认 5x），向上取整为积分（100 积分 =
     * 1 元），最低 1 积分。
     *
     * <p>图像输入统一按 fileId 传递并校验所有权，避免本地存储场景下把受权限保护的 {@code /api/system/files/{id}/content} URL 直接交给外部
     * SDK 下载（会 401）。真正的图像输入串（本地 data: URL / OSS 签名 URL）延迟到执行时通过 {@link
     * FileStoragePort#resolveImageData} 解析。
     *
     * @param userId 用户 ID
     * @param imageFileId 待处理图像的文件 ID
     * @param method 处理方式（如 SEGMENT_HD_BODY）
     * @param projectId 所属项目 ID，可空
     * @return 任务 ID
     */
    @Transactional
    public Long submitImageProcessTask(
            Long userId, Long imageFileId, String method, Long projectId) {
        if (imageFileId == null) {
            throw exception(ErrorCodeConstants.AIGC_TASK_IMAGE_URL_EMPTY);
        }
        if (method == null || method.isBlank()) {
            throw exception(ErrorCodeConstants.AIGC_TASK_METHOD_EMPTY);
        }
        var sourceFile = fileService.requireCurrentOwner(imageFileId);

        // 0.002~0.007 元/次 × 100 积分/元 × 加价倍率，向上取整，最低 1 积分
        long estimatedCost =
                Math.max(
                        1L,
                        (long)
                                Math.ceil(
                                        imageProcessPriceYuan(method)
                                                * AiCreditGuard.YUAN_TO_CREDIT
                                                * creditGuard.getMarkupRate()));
        creditGuard.precheck(
                userId, CreditTransactionCategoryEnum.IMAGE_PROCESS.getCode(), estimatedCost);

        var task =
                buildTask(
                        userId,
                        AigcTaskTypeEnum.IMAGE_PROCESS.getCode(),
                        sourceFile.url(),
                        "aliyun:imageseg",
                        "阿里云图像处理",
                        projectId);
        task.setParams(
                JsonUtils.toJsonString(Map.of("imageFileId", imageFileId, "method", method)));

        // 固定单价即时扣减（图像分割非模型驱动计费，不走 settleByUsage），失败时任务转失败并自动退还
        Long creditTxId =
                creditGuard.settleFixedReturningTxId(
                        userId,
                        estimatedCost,
                        CreditTransactionCategoryEnum.IMAGE_PROCESS.getCode(),
                        "图像处理-" + method);
        task.setCreditTxId(creditTxId);
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));

        final Long taskId = task.getId();
        final String mockUrl = isMockEnabled() ? getMockValue("image") : null;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        taskExecutor.submitImageProcessSync(taskId, imageFileId, method, mockUrl);
                    }
                });
        log.info("[submitImageProcessTask] 图像处理任务已创建: taskId={}, method={}", task.getId(), method);
        return task.getId();
    }

    // ========== 积分预估（不提交任务） ==========

    /**
     * 预估积分消耗——复用 submit 的路由+估算逻辑，不写库、不触发执行。
     *
     * @param userId 当前用户 ID
     * @param type 任务类型（IMAGE/VIDEO/MODEL_3D/MUSIC/VOICE）
     * @param model 指定模型（可空，空则走默认路由）
     * @param params 任务参数（与 submit 相同结构）
     * @return 预估积分消耗
     */
    public long estimateCredits(
            Long userId, String type, String model, Map<String, Object> params) {
        if (params == null) params = Map.of();
        int markup = creditGuard.getMarkupRate();
        AigcTaskTypeEnum taskType;
        try {
            taskType = AigcTaskTypeEnum.fromCode(type);
        } catch (IllegalArgumentException e) {
            throw exception(ErrorCodeConstants.AIGC_TASK_TYPE_INVALID, type);
        }
        return switch (taskType) {
            case IMAGE -> {
                var ctx =
                        CapabilityRoutingContext.of(
                                userId, CapabilityRoutingContext.CAP_IMAGE_GEN, model);
                var resolvedModel = capabilityRouter.resolve(ctx);
                int wRaw = toInt(params.get("width"), 0);
                int hRaw = toInt(params.get("height"), 0);
                // 0 或未传视为 auto，传 null 让后端用模型默认尺寸
                Integer w = wRaw > 0 ? wRaw : null;
                Integer h = hRaw > 0 ? hRaw : null;
                var req =
                        new ImageTaskRequest(
                                "",
                                resolvedModel.getModelId(),
                                w,
                                h,
                                null,
                                null,
                                null,
                                toInt(params.get("imageCount"), 1),
                                null,
                                toStr(params.get("quality")),
                                toStr(params.get("format")),
                                toStr(params.get("background")),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null);
                yield aiServiceRegistry
                        .get(ImageGenerationService.class, resolvedModel)
                        .estimateCost(resolvedModel, req, markup);
            }
            case VIDEO -> {
                var ctx =
                        CapabilityRoutingContext.of(
                                userId, CapabilityRoutingContext.CAP_VIDEO_GEN, model);
                var resolvedModel = capabilityRouter.resolve(ctx);
                var videoReq =
                        new com.xuejiai.aaf.framework.intelligent.ai.video.vo.VideoRequest(
                                "",
                                null,
                                null,
                                resolvedModel.getModelId(),
                                toStr(params.get("resolution")),
                                toStr(params.get("ratio")),
                                toInt(params.get("duration"), null),
                                null,
                                null);
                yield aiServiceRegistry
                        .get(VideoGenerationService.class, resolvedModel)
                        .estimateCost(resolvedModel, videoReq, markup);
            }
            case MODEL_3D -> {
                var ctx =
                        CapabilityRoutingContext.of(
                                userId, CapabilityRoutingContext.CAP_MODEL_3D, model);
                var resolvedModel = capabilityRouter.resolve(ctx);
                var req =
                        buildModel3dRequest(
                                "",
                                toStr(params.get("source")),
                                toStr(params.get("textureQuality")));
                yield model3dGenerationService.estimateCost(resolvedModel, req, markup);
            }
            case MUSIC -> {
                var ctx =
                        CapabilityRoutingContext.of(
                                userId, CapabilityRoutingContext.CAP_MUSIC_GEN, model);
                var resolvedModel = capabilityRouter.resolve(ctx);
                var req = new MusicGenerationService.MusicRequest("", null, null, "mp3");
                yield aiServiceRegistry
                        .get(MusicGenerationService.class, resolvedModel)
                        .estimateCost(resolvedModel, req, markup);
            }
            case VOICE -> {
                var ctx =
                        CapabilityRoutingContext.of(
                                userId, CapabilityRoutingContext.CAP_SPEECH_TTS, model);
                var resolvedModel = capabilityRouter.resolve(ctx);
                String text = toStr(params.get("text"));
                yield aiServiceRegistry
                        .get(SpeechService.class, resolvedModel)
                        .estimateCost(resolvedModel, text != null ? text : "", markup);
            }
            case IMAGE_PROCESS -> {
                String method = toStr(params.get("method"));
                yield Math.max(
                        1L,
                        (long)
                                Math.ceil(
                                        imageProcessPriceYuan(method)
                                                * AiCreditGuard.YUAN_TO_CREDIT
                                                * markup));
            }
        };
    }

    private static int toInt(Object val, Integer defaultVal) {
        if (val == null) return defaultVal != null ? defaultVal : 0;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return defaultVal != null ? defaultVal : 0;
        }
    }

    private static String toStr(Object val) {
        return val == null ? null : val.toString();
    }

    // ========== 任务完成/失败回调 ==========

    /** 图片等远程结果完成时，将供应商 URL 持久化为平台媒体。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTask(String providerTaskId, String resultUrl) {
        var task = taskRepo.findByProviderTaskId(providerTaskId).orElse(null);
        if (task == null) {
            log.warn("[completeTask] 任务不存在: providerTaskId={}", providerTaskId);
            return;
        }
        if (!isCompletable(task)) {
            log.info(
                    "[completeTask] 忽略终态任务回调: taskId={}, status={}",
                    task.getId(),
                    task.getStatus());
            return;
        }
        if (!AigcTaskTypeEnum.IMAGE.getCode().equals(task.getType())) {
            throw new IllegalStateException("URL 完成回调仅支持 IMAGE 任务: " + task.getType());
        }
        task.setProviderResult(JsonUtils.toJsonString(Map.of("url", resultUrl)));

        try {
            var file = uploadFileStrict(resultUrl, task);
            var media =
                    createMedia(
                            task,
                            file,
                            AigcMediaType.IMAGE,
                            generatedName(task),
                            null,
                            null,
                            null,
                            null,
                            generationInfo(task));
            completeWithMedia(task, media);
        } catch (Exception e) {
            log.error("[completeTask] 结果持久化失败，转 failTask: taskId={}", task.getId(), e);
            failTask(providerTaskId, "生成结果持久化失败: " + e.getMessage());
            return;
        }

        publishCompleted(task);
        eventPublisher.publishEvent(new UserGrowthEvent(task.getUserId(), "aigc.image.success"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTask(String providerTaskId, VideoTaskResult result) {
        var task = taskRepo.findByProviderTaskId(providerTaskId).orElse(null);
        if (task == null) {
            log.warn("[completeTask] 任务不存在: providerTaskId={}", providerTaskId);
            return;
        }
        if (!isCompletable(task)) {
            log.info(
                    "[completeTask] 忽略终态任务回调: taskId={}, status={}",
                    task.getId(),
                    task.getStatus());
            return;
        }
        task.setProviderResult(JsonUtils.toJsonString(result));

        Long creditTxId = null;
        try {
            var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
            creditTxId =
                    creditGuard.settleByUsageReturningTxId(
                            task.getUserId(),
                            aiModel,
                            result,
                            CreditTransactionCategoryEnum.VIDEO.getCode(),
                            "视频生成");
        } catch (Exception e) {
            log.warn("[completeTask] 积分结算失败: taskId={}, err={}", task.getId(), e.getMessage());
        }
        if (creditTxId != null) {
            task.setCreditTxId(creditTxId);
            taskRepo.save(task);
        }

        try {
            var file = uploadFileStrict(result.getVideoUrl(), task);
            var media =
                    createMedia(
                            task,
                            file,
                            AigcMediaType.VIDEO,
                            generatedName(task),
                            null,
                            null,
                            result.getDuration() != null
                                    ? BigDecimal.valueOf(result.getDuration())
                                    : null,
                            null,
                            generationInfo(task));
            completeWithMedia(task, media);
        } catch (Exception e) {
            log.error("[completeTask] 视频结果持久化失败，转 failTask: taskId={}", task.getId(), e);
            failTask(providerTaskId, "生成结果持久化失败: " + e.getMessage());
            return;
        }

        publishCompleted(task);
        eventPublisher.publishEvent(new UserGrowthEvent(task.getUserId(), "aigc.video.success"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTask(
            String providerTaskId, Model3dGenerationService.Model3dTaskResult result) {
        var task = taskRepo.findByProviderTaskId(providerTaskId).orElse(null);
        if (task == null) {
            log.warn("[completeTask] 任务不存在: providerTaskId={}", providerTaskId);
            return;
        }
        if (!isCompletable(task)) {
            log.info(
                    "[completeTask] 忽略终态任务回调: taskId={}, status={}",
                    task.getId(),
                    task.getStatus());
            return;
        }
        task.setProviderResult(JsonUtils.toJsonString(result));

        Long creditTxId = settleModel3d(task, providerTaskId);
        if (creditTxId != null) {
            task.setCreditTxId(creditTxId);
            taskRepo.save(task);
        }

        var modelUrl = result.modelUrl() != null ? result.modelUrl() : result.baseModelUrl();
        try {
            var file = uploadFileStrict(modelUrl, task);
            var media =
                    createMedia(
                            task,
                            file,
                            AigcMediaType.MODEL_3D,
                            generatedName(task),
                            null,
                            null,
                            null,
                            null,
                            generationInfo(task));
            completeWithMedia(task, media);
        } catch (Exception e) {
            log.error("[completeTask] 3D 结果持久化失败，转 failTask: taskId={}", task.getId(), e);
            failTask(providerTaskId, "生成结果持久化失败: " + e.getMessage());
            return;
        }

        publishCompleted(task);
    }

    /**
     * 3D 任务积分结算：从 task.params 读 source/textureQuality，按实际参数结算并返回 creditTxId。
     *
     * @return 写入的 creditTxId；结算失败返回 null
     */
    private Long settleModel3d(AigcTask task, String thirdTaskId) {
        try {
            var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
            Map<String, Object> p =
                    task.getParams() != null
                            ? JsonUtils.parseObject(
                                    task.getParams(), new TypeReference<Map<String, Object>>() {})
                            : Map.of();
            String source = p.containsKey("source") ? (String) p.get("source") : "text";
            String texture =
                    p.containsKey("textureQuality") ? (String) p.get("textureQuality") : "none";
            var usage =
                    new Model3dGenerationService.Model3dTaskResult(
                            thirdTaskId, null, null, null, null, null, source, texture);
            log.info(
                    "[completeTask] 3D 积分结算: taskId={}, source={}, texture={}",
                    task.getId(),
                    source,
                    texture);
            return creditGuard.settleByUsageReturningTxId(
                    task.getUserId(),
                    aiModel,
                    usage,
                    CreditTransactionCategoryEnum.MODEL_3D.getCode(),
                    "3D 生成");
        } catch (Exception e) {
            log.warn("[completeTask] 3D 积分结算失败: taskId={}, err={}", task.getId(), e.getMessage());
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failTask(String thirdTaskId, String errorMsg) {
        if (thirdTaskId == null) return;
        var task = taskRepo.findByProviderTaskId(thirdTaskId).orElse(null);
        if (task == null) {
            log.warn("[failTask] 任务不存在: thirdTaskId={}", thirdTaskId);
            return;
        }

        // 关键：若已扣过积分，则触发退还（写反向 EARN 流水）
        if (task.getCreditTxId() != null) {
            try {
                Long refundTxId =
                        creditGuard.refund(
                                task.getCreditTxId(),
                                ("AIGC 任务失败自动退还: " + errorMsg)
                                        .substring(
                                                0,
                                                Math.min(
                                                        200,
                                                        ("AIGC 任务失败自动退还: " + errorMsg).length())));
                if (refundTxId != null) {
                    log.info(
                            "[failTask] 积分已退还: taskId={}, originalTxId={}, refundTxId={}",
                            task.getId(),
                            task.getCreditTxId(),
                            refundTxId);
                }
            } catch (Exception e) {
                log.warn(
                        "[failTask] 积分退还失败（不影响任务状态）: taskId={}, err={}",
                        task.getId(),
                        e.getMessage());
            }
        }

        task.setStatus(AigcTaskStatusEnum.FAIL.getCode());
        task.setErrorMsg(errorMsg);
        taskRepo.save(task);
        eventService.push(task.getUserId(), EVENT_FAILED, toVO(task));
        eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
        log.info("[failTask] 任务失败: taskId={}, reason={}", task.getId(), errorMsg);
    }

    /** 查询指定用户今日创建的任务数。 */
    public long todayCountByUser(Long userId) {
        return taskRepo.countByUserIdAndCreateTimeAfter(
                userId, java.time.LocalDate.now().atStartOfDay());
    }

    // ========== 内部工具方法 ==========

    /** 为已创建任务设置技能 systemPrompt（Controller 提交任务后回写）。 忽略 taskId 不存在的情况（防御性处理）。 */
    @Transactional
    public void setSystemPrompt(Long taskId, String systemPrompt) {
        if (taskId == null || systemPrompt == null || systemPrompt.isBlank()) return;
        taskRepo.findById(taskId)
                .ifPresent(
                        task -> {
                            task.setSystemPrompt(systemPrompt);
                            taskRepo.save(task);
                        });
    }

    /**
     * 按处理方式返回阿里云图像分割单价（元/次）。
     *
     * <ul>
     *   <li>SEGMENT_HD_BODY — 0.007 元（高清人体分割）
     *   <li>SEGMENT_COMMON_IMAGE 及其他 — 0.002 元（通用分割）
     * </ul>
     */
    private double imageProcessPriceYuan(String method) {
        if ("SEGMENT_HD_BODY".equals(method)) {
            return IMAGE_PROCESS_HD_BODY_PRICE_YUAN;
        }
        return IMAGE_PROCESS_COMMON_PRICE_YUAN;
    }

    private AigcTask buildTask(
            Long userId,
            String type,
            String prompt,
            String model,
            String modelName,
            Long projectId) {
        var task = new AigcTask();
        task.setUserId(userId);
        task.setOwnerId(userId);
        task.setType(type);
        task.setStatus(AigcTaskStatusEnum.PENDING.getCode());
        task.setPrompt(prompt);
        task.setModel(model);
        task.setModelName(modelName);
        task.setProjectId(projectId);
        if (model != null) {
            int colon = model.indexOf(':');
            task.setProvider(colon > 0 ? model.substring(0, colon) : model);
        }
        return task;
    }

    private StoredFile uploadFileStrict(String url, AigcTask task) {
        String ext = guessExtension(url, task.getType());
        String path =
                "aigc/%s/%s.%s".formatted(task.getType().toLowerCase(), UUID.randomUUID(), ext);
        try {
            return fileService.uploadFromUrl(
                    url, path, guessContentType(task.getType()), task.getUserId());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "AIGC 任务文件持久化失败: taskId=" + task.getId() + ", url=" + url, e);
        }
    }

    private AigcMediaView createMedia(
            AigcTask task,
            StoredFile file,
            AigcMediaType mediaType,
            String name,
            Integer width,
            Integer height,
            BigDecimal duration,
            BigDecimal frameRate,
            String generationInfo) {
        return mediaApi.createFromGeneratedFile(
                new AigcGeneratedMediaCommand(
                        task.getUserId(),
                        name,
                        mediaType,
                        file,
                        null,
                        null,
                        task.getId(),
                        task.getProjectId(),
                        width,
                        height,
                        duration,
                        frameRate,
                        generationInfo));
    }

    private boolean isCompletable(AigcTask task) {
        return AigcTaskStatusEnum.PENDING.getCode().equals(task.getStatus())
                || AigcTaskStatusEnum.RUNNING.getCode().equals(task.getStatus());
    }

    private void completeWithMedia(AigcTask task, AigcMediaView media) {
        task.setOutputMediaVersionId(media.currentVersion().id());
        task.setStatus(AigcTaskStatusEnum.SUCCESS.getCode());
        taskRepo.save(task);
    }

    private void publishCompleted(AigcTask task) {
        eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
        eventPublisher.publishEvent(AigcTaskTerminalEvent.from(task));
        log.info(
                "[completeTask] 任务完成: taskId={}, mediaVersionId={}",
                task.getId(),
                task.getOutputMediaVersionId());
    }

    private String generatedName(AigcTask task) {
        var source =
                task.getPrompt() != null && !task.getPrompt().isBlank()
                        ? task.getPrompt()
                        : "AI生成-" + task.getType() + "-" + task.getId();
        return source.substring(0, Math.min(source.length(), 80));
    }

    private String generationInfo(AigcTask task) {
        return JsonUtils.toJsonString(
                Map.of(
                        "prompt", task.getPrompt() != null ? task.getPrompt() : "",
                        "model", task.getModel() != null ? task.getModel() : "",
                        "provider", task.getProvider() != null ? task.getProvider() : ""));
    }

    /** 任务类型 → 文件扩展名映射，取值参见 {@link AigcTaskTypeEnum}（case 需编译期常量，故用字面量）。 */
    private String guessExtension(String url, String type) {
        if (url != null && url.contains(".")) {
            String path = url.split("\\?")[0];
            String[] parts = path.split("\\.");
            String ext = parts[parts.length - 1].toLowerCase();
            if (ext.length() <= 5) return ext;
        }
        return switch (type) {
            case "VIDEO" -> "mp4";
            case "MODEL_3D" -> "glb";
            case "MUSIC" -> "mp3";
            case "VOICE" -> "mp3";
            default -> "png";
        };
    }

    /** 任务类型 → Content-Type 映射，取值参见 {@link AigcTaskTypeEnum}（case 需编译期常量，故用字面量）。 */
    private String guessContentType(String type) {
        return switch (type) {
            case "VIDEO" -> "video/mp4";
            case "MODEL_3D" -> "model/gltf-binary";
            case "MUSIC" -> "audio/mpeg";
            case "VOICE" -> "audio/mpeg";
            default -> "image/png";
        };
    }

    // ========== Mock 辅助方法 ==========

    /** 判断 AIGC Mock 开关是否开启。 */
    private boolean isMockEnabled() {
        return systemConfigService.getBoolean(SysConfigKeys.Aigc.MOCK_ENABLED, false);
    }

    /**
     * 读取指定类型的 mock 固定返回值。
     *
     * <p>从 {@code aigc.mock_data} JSON 中按 {@code typeKey} 取值，JSON 示例：
     *
     * <pre>
     * {@code {"image":"https://...","video":"https://...","text":"固定文字","audio":"https://..."}}
     * </pre>
     *
     * @param typeKey image / video / text / audio
     * @return 固定返回值，配置缺失时返回空字符串
     */
    private String getMockValue(String typeKey) {
        var json = systemConfigService.getString(SysConfigKeys.Aigc.MOCK_DATA);
        if (json == null || json.isBlank()) return "";
        try {
            var map = JsonUtils.parseObject(json, new TypeReference<Map<String, String>>() {});
            return map.getOrDefault(typeKey, "");
        } catch (Exception e) {
            log.warn("[getMockValue] 解析 aigc.mock_data 失败: {}", e.getMessage());
            return "";
        }
    }
}
