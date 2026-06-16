package com.xuejiai.aaf.module.ai.aigc.task.service;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.mapper.AigcTaskMapper;
import com.xuejiai.aaf.module.ai.aigc.task.repository.AigcTaskRepository;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskPageDTO;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;
import com.xuejiai.aaf.module.ai.aigc.task.vo.ImageTaskRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final StorageService storageService;
    private final MediaAssetService mediaAssetService;
    private final ImageServiceFactory imageServiceFactory;
    private final CapabilityRouter capabilityRouter;
    private final AigcTaskExecutor taskExecutor;
    private final AiCreditGuard creditGuard;

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
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (dto.getUserId() != null)
                predicates.add(cb.equal(root.get("userId"), dto.getUserId()));
            if (dto.getType() != null) predicates.add(cb.equal(root.get("type"), dto.getType()));
            if (dto.getStatus() != null)
                predicates.add(cb.equal(root.get("status"), dto.getStatus()));
            if (dto.getProjectId() != null)
                predicates.add(cb.equal(root.get("projectId"), dto.getProjectId()));
            return predicates.isEmpty()
                    ? null
                    : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected String entityName() {
        return "AIGC任务";
    }

    // ========== 提交任务 ==========

    @Transactional
    public Long submitImageTask(Long userId, ImageTaskRequest req) {
        creditGuard.precheck(userId, "image-gen");
        long t0 = System.currentTimeMillis();
        var ctx =
                CapabilityRoutingContext.of(
                        userId, CapabilityRoutingContext.CAP_IMAGE_GEN, req.model());
        var resolvedAiModel = capabilityRouter.resolve(ctx);
        // 用模型单价估算本次花费，精确拦截余额不足
        int markup = 10; // 与 DefaultAiCreditGuard 保持一致的默认倍率
        long estimatedCost = 1;
        if (resolvedAiModel.getModelPrice() != null) {
            estimatedCost =
                    Math.max(1, Math.round(resolvedAiModel.getModelPrice().doubleValue() * markup));
        }
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
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                taskExecutor.submitSync(taskId, prompt, resolvedModel);
                            }
                        });
        log.info(
                "[submitImageTask] 总耗时: {}ms, taskId={}",
                System.currentTimeMillis() - t0,
                task.getId());
        return task.getId();
    }

    @Transactional
    public Long submitVideoTask(Long userId, String prompt, String model, Long projectId) {
        var task = buildTask(userId, TYPE_VIDEO, prompt, model, null, projectId);
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));
        log.info("[submitVideoTask] 视频生成任务已创建: taskId={}, model={}", task.getId(), model);
        return task.getId();
    }

    @Transactional
    public Long submit3dTask(Long userId, String prompt, String model, Long projectId) {
        var task = buildTask(userId, TYPE_MODEL3D, prompt, model, null, projectId);
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));

        final Long taskId = task.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                taskExecutor.submitModel3dSync(taskId, prompt);
                            }
                        });
        return task.getId();
    }

    @Transactional
    public Long submitMusicTask(
            Long userId,
            String prompt,
            String model,
            String lyrics,
            String gender,
            Long projectId) {
        creditGuard.precheck(userId, "music-gen");
        var task = buildTask(userId, TYPE_MUSIC, prompt, model, null, projectId);
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));

        final Long taskId = task.getId();
        final String resolvedGender = gender != null ? gender : "female";
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                taskExecutor.submitMusicSync(
                                        taskId, prompt, lyrics, resolvedGender);
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
        creditGuard.precheck(userId, "voice-gen");
        if (text == null || text.isBlank()) {
            throw new com.xuejiai.aaf.common.exception.BusinessException(
                    com.xuejiai.aaf.common.exception.GlobalErrorCode.BAD_REQUEST, "配音文本不能为空");
        }
        if (text.length() > VOICE_TEXT_MAX_LEN) {
            throw new com.xuejiai.aaf.common.exception.BusinessException(
                    com.xuejiai.aaf.common.exception.GlobalErrorCode.BAD_REQUEST,
                    "配音文本不能超过 " + VOICE_TEXT_MAX_LEN + " 字");
        }

        var task = buildTask(userId, TYPE_VOICE, text, model, null, projectId);
        if (voice != null && !voice.isBlank()) {
            task.setParams("{\"voice\":\"" + voice.replace("\"", "'") + "\"}");
        }
        taskRepo.save(task);
        eventService.push(userId, EVENT_CREATED, toVO(task));

        final Long taskId = task.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                taskExecutor.submitVoiceSync(taskId, text, voice);
                            }
                        });
        log.info("[submitVoiceTask] 配音生成任务已创建: taskId={}, voice={}", task.getId(), voice);
        return task.getId();
    }

    // ========== 任务完成/失败回调 ==========

    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void completeTask(String thirdTaskId, String resultUrl) {
        var task = taskRepo.findByTaskId(thirdTaskId).orElse(null);
        if (task == null) {
            log.warn("[completeTask] 任务不存在: thirdTaskId={}", thirdTaskId);
            return;
        }
        task.setResultUrl(resultUrl);
        String ossUrl = uploadToOss(resultUrl, task.getType(), task.getId());
        task.setOssUrl(ossUrl);
        task.setStatus(STATUS_SUCCESS);
        taskRepo.save(task);
        saveToMediaAsset(task, ossUrl);
        eventService.push(task.getUserId(), EVENT_COMPLETED, toVO(task));
        log.info("[completeTask] 任务完成: taskId={}, ossUrl={}", task.getId(), ossUrl);
    }

    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
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

    private String uploadToOss(String url, String type, Long taskId) {
        try {
            String ext = guessExtension(url, type);
            String filename = "aigc/%s/%s.%s".formatted(type.toLowerCase(), UUID.randomUUID(), ext);
            String contentType = guessContentType(type);
            try (InputStream is = URI.create(url).toURL().openStream()) {
                String key = storageService.upload(is, filename, contentType);
                return storageService.getUrl(key);
            }
        } catch (Exception e) {
            log.warn("[uploadToOss] 上传 OSS 失败，回退使用原始 URL: taskId={}, url={}", taskId, url, e);
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
                            "{\"prompt\":\"%s\",\"model\":\"%s\"}"
                                    .formatted(
                                            task.getPrompt() != null
                                                    ? task.getPrompt().replace("\"", "'")
                                                    : "",
                                            task.getModel() != null ? task.getModel() : ""),
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
}
