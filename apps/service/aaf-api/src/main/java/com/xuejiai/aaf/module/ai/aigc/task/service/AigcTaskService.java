package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xuejiai.aaf.common.constant.SysConfigKeys;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageResult;
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
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.mapper.AigcTaskMapper;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskPageDTO;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;
import com.xuejiai.aaf.module.ai.aigc.task.vo.ImageTaskRequest;
import com.xuejiai.aaf.module.ai.aigc.task.vo.VideoTaskRequest;
import com.xuejiai.aaf.module.system.file.service.FileUploadService;

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

    private static final String TYPE_IMAGE = "IMAGE";
    private static final String TYPE_VIDEO = "VIDEO";
    private static final String TYPE_MODEL3D = "MODEL_3D";
    private static final String TYPE_MUSIC = "MUSIC";
    private static final String TYPE_VOICE = "VOICE";

    /** 配音文本最大长度（字） */
    private static final int VOICE_TEXT_MAX_LEN = 200;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";

    private static final String EVENT_CREATED = "task.created";
    private static final String EVENT_COMPLETED = "task.completed";
    private static final String EVENT_FAILED = "task.failed";

    private final AigcTaskRepository taskRepo;
    private final AigcTaskEventService eventService;
    private final AigcTaskMapper taskMapper;
    private final FileUploadService fileService;
    private final MediaAssetService mediaAssetService;
    private final CapabilityRouter capabilityRouter;
    private final AigcTaskExecutor taskExecutor;
    private final AiCreditGuard creditGuard;
    private final SystemConfigService systemConfigService;
    private final ConfigCacheManager configCacheManager;
    private final AiServiceRegistry aiServiceRegistry;
    private final Model3dGenerationService model3dGenerationService;

    // ========== BaseCrudService 必须实现 ==========

    @Override
    protected JpaRepository<AigcTask, Long> getRepository() {
        return taskRepo;
    }

    @Override
    protected JpaSpecificationExecutor<AigcTask> getSpecExecutor() {
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

    @Override
    protected String entityName() {
        return "AIGC任务";
    }

    // ========== 提交任务 ==========

    @Transactional
    public Long submitImageTask(Long userId, ImageTaskRequest req) {
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
        creditGuard.precheck(userId, "image-gen", estimatedCost);
        log.debug("[submitImageTask] resolve 耗时: {}ms", System.currentTimeMillis() - t0);
        String resolvedModel = resolvedAiModel.getModelId();

        var task =
                buildTask(
                        userId,
                        TYPE_IMAGE,
                        req.prompt(),
                        resolvedModel,
                        resolvedAiModel.getDisplayName(),
                        req.projectId());
        task.setParams(req.toParamsJson());
        taskRepo.save(task);
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
                        req.imageUrl(),
                        req.referenceImageUrls(),
                        resolvedModelId,
                        req.resolution(),
                        req.ratio(),
                        req.duration(),
                        req.seed(),
                        imageModeEnum);
        var svc = aiServiceRegistry.get(VideoGenerationService.class, resolvedModel);
        long estimatedCost = svc.estimateCost(resolvedModel, videoReq, creditGuard.getMarkupRate());
        creditGuard.precheck(userId, "video-gen", estimatedCost);

        var task =
                buildTask(
                        userId,
                        TYPE_VIDEO,
                        req.prompt(),
                        resolvedModelId,
                        resolvedModel.getDisplayName(),
                        req.projectId());
        task.setParams(req.toParamsJson());

        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));
        if (isMockEnabled()) {
            mockComplete(task, "video");
            return task.getId();
        }

        final Long taskId = task.getId();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        taskExecutor.submitVideoAsync(taskId, req.prompt(), resolvedModelId, null);
                    }
                });
        log.info("[submitVideoTask] 视频生成任务已创建: taskId={}, model={}", task.getId(), resolvedModelId);
        return task.getId();
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
        creditGuard.precheck(userId, "model3d-gen", estimatedCost);

        var task =
                buildTask(
                        userId,
                        TYPE_MODEL3D,
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
        var resolvedModel = configCacheManager.getAiModelByModelId(model);
        var musicReq = new MusicGenerationService.MusicRequest(prompt, lyrics, gender, "mp3");
        long estimatedCost =
                aiServiceRegistry
                        .get(MusicGenerationService.class, resolvedModel)
                        .estimateCost(resolvedModel, musicReq, creditGuard.getMarkupRate());
        creditGuard.precheck(userId, "music-gen", estimatedCost);
        var task = buildTask(userId, TYPE_MUSIC, prompt, model, null, projectId);
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
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "配音文本不能为空");
        }
        if (text.length() > VOICE_TEXT_MAX_LEN) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "配音文本不能超过 " + VOICE_TEXT_MAX_LEN + " 字");
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
        creditGuard.precheck(userId, "voice-gen", estimatedCost);

        var task =
                buildTask(
                        userId,
                        TYPE_VOICE,
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

    // ========== 任务完成/失败回调 ==========

    /** 图片/3D 等非视频任务完成时，直接传入 resultUrl，无需 VideoTaskResult。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTask(String thirdTaskId, String resultUrl) {
        var task = taskRepo.findByTaskId(thirdTaskId).orElse(null);
        if (task == null) {
            log.warn("[completeTask] 任务不存在: thirdTaskId={}", thirdTaskId);
            return;
        }
        task.setResultUrl(resultUrl);
        String ossUrl = uploadFile(resultUrl, task.getType(), task.getId());
        task.setOssUrl(ossUrl);
        task.setStatus(STATUS_SUCCESS);
        taskRepo.save(task);
        saveToMediaAsset(task, ossUrl);

        // 3D 任务：从 task.params 读 source/textureQuality 结算积分
        if (TYPE_MODEL3D.equals(task.getType())) {
            try {
                var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
                Map<String, Object> p =
                        task.getParams() != null
                                ? JsonUtils.parseObject(
                                        task.getParams(),
                                        new TypeReference<Map<String, Object>>() {})
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
                creditGuard.settleByUsage(task.getUserId(), aiModel, usage, "model3d-gen", "3D 生成");
            } catch (Exception e) {
                log.warn(
                        "[completeTask] 3D 积分结算失败: taskId={}, err={}",
                        task.getId(),
                        e.getMessage());
            }
        }

        eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
        log.info("[completeTask] 任务完成: taskId={}, ossUrl={}", task.getId(), ossUrl);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTask(String thirdTaskId, VideoTaskResult result) {
        var task = taskRepo.findByTaskId(thirdTaskId).orElse(null);
        if (task == null) {
            log.warn("[completeTask] 任务不存在: thirdTaskId={}", thirdTaskId);
            return;
        }
        task.setResultUrl(result.getVideoUrl());
        String ossUrl = uploadFile(result.getVideoUrl(), task.getType(), task.getId());
        task.setOssUrl(ossUrl);
        task.setStatus(STATUS_SUCCESS);
        taskRepo.save(task);
        saveToMediaAsset(task, ossUrl);

        // 按实际 duration + resolution 结算积分
        try {
            var aiModel = configCacheManager.getAiModelByModelId(task.getModel());
            creditGuard.settleByUsage(task.getUserId(), aiModel, result, "video-gen", "视频生成");
        } catch (Exception e) {
            log.warn("[completeTask] 积分结算失败: taskId={}, err={}", task.getId(), e.getMessage());
        }

        eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
        log.info("[completeTask] 任务完成: taskId={}, ossUrl={}", task.getId(), ossUrl);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failTask(String thirdTaskId, String errorMsg) {
        if (thirdTaskId == null) return;
        var task = taskRepo.findByTaskId(thirdTaskId).orElse(null);
        if (task == null) {
            log.warn("[failTask] 任务不存在: thirdTaskId={}", thirdTaskId);
            return;
        }
        task.setStatus(STATUS_FAIL);
        task.setErrorMsg(errorMsg);
        taskRepo.save(task);
        eventService.push(task.getUserId(), EVENT_FAILED, toVO(task));
        log.info("[failTask] 任务失败: taskId={}, reason={}", task.getId(), errorMsg);
    }

    // ========== 兼容旧分页查询（Controller 过渡用） ==========

    @Transactional(readOnly = true)
    public PageResult<AigcTaskVO> pageByUser(Long userId, int pageNo, int pageSize) {
        var dto = new AigcTaskPageDTO();
        dto.setUserId(userId);
        dto.setPageNo(pageNo);
        dto.setPageSize(pageSize);
        return page(dto);
    }

    // ========== 内部工具方法 ==========

    private AigcTask buildTask(
            Long userId,
            String type,
            String prompt,
            String model,
            String modelName,
            Long projectId) {
        var task = new AigcTask();
        task.setUserId(userId);
        task.setType(type);
        task.setStatus(STATUS_PENDING);
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

    private String uploadFile(String url, String type, Long taskId) {
        try {
            String ext = guessExtension(url, type);
            String path = "aigc/%s/%s.%s".formatted(type.toLowerCase(), UUID.randomUUID(), ext);
            return fileService.uploadFromUrl(url, path, guessContentType(type), null);
        } catch (Exception e) {
            log.warn("[uploadFile] 上传文件失败，回退使用原始 URL: taskId={}, url={}", taskId, url, e);
            return url;
        }
    }

    private void saveToMediaAsset(AigcTask task, String ossUrl) {
        try {
            var dto =
                    new SaveFromGenerationDTO(
                            "AI生成-" + task.getType() + "-" + task.getId(),
                            toMediaAssetType(task.getType()),
                            ossUrl,
                            null,
                            JsonUtils.toJsonString(
                                    Map.of(
                                            "prompt",
                                                    task.getPrompt() != null
                                                            ? task.getPrompt()
                                                            : "",
                                            "model",
                                                    task.getModel() != null
                                                            ? task.getModel()
                                                            : "")),
                            null,
                            null,
                            null,
                            null,
                            null,
                            true,
                            task.getModelName(),
                            task.getProvider(),
                            task.getProjectId());
            mediaAssetService.saveFromGeneration(task.getUserId(), dto);
        } catch (Exception e) {
            log.warn("[saveToMediaAsset] 写入素材库失败: taskId={}", task.getId(), e);
        }
    }

    private MediaAssetType toMediaAssetType(String type) {
        return switch (type) {
            case TYPE_VIDEO -> MediaAssetType.VIDEO;
            case TYPE_MODEL3D -> MediaAssetType.MODEL_3D;
            case TYPE_MUSIC -> MediaAssetType.AUDIO;
            case TYPE_VOICE -> MediaAssetType.AUDIO;
            default -> MediaAssetType.IMAGE;
        };
    }

    private String guessExtension(String url, String type) {
        if (url != null && url.contains(".")) {
            String path = url.split("\\?")[0];
            String[] parts = path.split("\\.");
            String ext = parts[parts.length - 1].toLowerCase();
            if (ext.length() <= 5) return ext;
        }
        return switch (type) {
            case TYPE_VIDEO -> "mp4";
            case TYPE_MODEL3D -> "glb";
            case TYPE_MUSIC -> "mp3";
            case TYPE_VOICE -> "mp3";
            default -> "png";
        };
    }

    private String guessContentType(String type) {
        return switch (type) {
            case TYPE_VIDEO -> "video/mp4";
            case TYPE_MODEL3D -> "model/gltf-binary";
            case TYPE_MUSIC -> "audio/mpeg";
            case TYPE_VOICE -> "audio/mpeg";
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

    /**
     * Mock 模式下将任务直接标记为成功，写入固定返回值，并同步写入素材库（image/video/model3d）。
     *
     * @param task 已保存的任务实体
     * @param typeKey image / video / text / audio / model3d
     */
    private void mockComplete(AigcTask task, String typeKey) {
        var mockVal = getMockValue(typeKey);
        task.setResultUrl(mockVal);
        task.setOssUrl(mockVal);
        task.setStatus(STATUS_SUCCESS);
        taskRepo.save(task);
        eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
        log.info("[mock] 任务直接完成: taskId={}, type={}, url={}", task.getId(), typeKey, mockVal);
    }
}
