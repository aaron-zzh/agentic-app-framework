package com.xuejiai.aaf.module.ai.aigc.task;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.intelligent.ai.image.DashScopeImageGenerationService;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageEditRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService.ImageRequest;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageGenerationService.ImageResult;
import com.xuejiai.aaf.framework.intelligent.ai.image.ImageServiceFactory;
import com.xuejiai.aaf.framework.storage.StorageService;
import com.xuejiai.aaf.module.ai.aigc.media.domain.MediaAssetGroup;
import com.xuejiai.aaf.module.ai.aigc.media.enums.MediaAssetType;
import com.xuejiai.aaf.module.ai.aigc.media.repository.MediaAssetGroupRepository;
import com.xuejiai.aaf.module.ai.aigc.media.service.MediaAssetService;
import com.xuejiai.aaf.module.ai.aigc.media.vo.SaveFromGenerationDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AIGC 任务异步执行器。
 *
 * <p>独立 Bean，确保 {@code @Async} 通过 Spring 代理生效（同 bean 内自调用无效）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigcTaskExecutor {

    private final AigcTaskRepository taskRepo;
    private final AigcTaskEventService eventService;
    private final StorageService storageService;
    private final MediaAssetService mediaAssetService;
    private final MediaAssetGroupRepository groupRepository;
    private final ImageServiceFactory imageServiceFactory;

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAIL = "FAIL";
    private static final String EVENT_COMPLETED = "task.completed";
    private static final String EVENT_FAILED = "task.failed";

    /**
     * 同步模型路径（所有图像生成模型统一入口）。
     *
     * <p>从 {@code task.params} 读取 width/height/negativePrompt/seed/promptExtend/imageCount， 构建完整
     * {@link ImageRequest} 后调用对应服务。
     */
    @Async
    @Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void submitSync(Long taskId, String prompt, String modelId) {
        var task = taskRepo.findById(taskId).orElse(null);
        if (task == null) return;
        try {
            task.setStatus(STATUS_RUNNING);
            taskRepo.save(task);

            // 从 params JSON 读取生成参数
            int w = 1024, h = 1024;
            String negativePrompt = null;
            int seed = 0;
            Boolean promptExtend = null;
            int count = 1;
            String quality = null;
            String format = null;
            String background = null;
            String contentModeration = null;
            String sizePreset = null;
            String aspectRatio = null;
            String displayPrompt = null;
            java.util.List<String> imageUrls = null;
            if (task.getParams() != null) {
                try {
                    var node =
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readTree(task.getParams());
                    w = node.path("width").asInt(1024);
                    h = node.path("height").asInt(1024);
                    if (node.has("negativePrompt"))
                        negativePrompt = node.get("negativePrompt").asText();
                    seed = node.path("seed").asInt(0);
                    if (node.has("promptExtend"))
                        promptExtend = node.get("promptExtend").asBoolean();
                    count = node.path("imageCount").asInt(1);
                    if (node.has("imageUrls") && node.get("imageUrls").isArray()) {
                        imageUrls = new java.util.ArrayList<>();
                        for (var n : node.get("imageUrls")) imageUrls.add(n.asText());
                    }
                    if (node.has("quality")) quality = node.get("quality").asText();
                    if (node.has("format")) format = node.get("format").asText();
                    if (node.has("background")) background = node.get("background").asText();
                    if (node.has("contentModeration"))
                        contentModeration = node.get("contentModeration").asText();
                    if (node.has("sizePreset")) sizePreset = node.get("sizePreset").asText();
                    if (node.has("aspectRatio")) aspectRatio = node.get("aspectRatio").asText();
                    if (node.has("displayPrompt"))
                        displayPrompt = node.get("displayPrompt").asText();
                } catch (Exception ignore) {
                }
            }

            var svc = imageServiceFactory.getSyncService(modelId);
            ImageResult result;
            if (imageUrls != null && !imageUrls.isEmpty()) {
                if (svc instanceof DashScopeImageGenerationService ds) {
                    // DashScope qwen-image-2.x：多图 base64 编辑
                    // wan2.x：图像编辑（传图片 URL）
                    String editSize =
                            (w != 1024 || h != 1024)
                                    ? w + "*" + h
                                    : (sizePreset != null ? sizePreset : null);
                    result =
                            ds.generateWithImages(
                                    modelId,
                                    prompt,
                                    imageUrls,
                                    editSize,
                                    seed,
                                    count,
                                    sizePreset != null);
                } else {
                    result =
                            svc.imageToImage(
                                    new ImageEditRequest(
                                            imageUrls.get(0),
                                            null,
                                            prompt,
                                            null,
                                            modelId,
                                            quality,
                                            format,
                                            background,
                                            contentModeration,
                                            count > 1 ? count : null,
                                            imageUrls));
                }
            } else {
                result =
                        svc.generate(
                                new ImageRequest(
                                        prompt,
                                        modelId,
                                        w,
                                        h,
                                        "url",
                                        negativePrompt,
                                        seed,
                                        promptExtend,
                                        count,
                                        quality,
                                        format,
                                        background,
                                        contentModeration,
                                        sizePreset,
                                        aspectRatio));
            }

            String ossUrl;
            String firstUrl = result.url() != null ? result.url()
                    : (result.urls() != null && !result.urls().isEmpty() ? result.urls().get(0) : null);
            if (firstUrl != null) {
                // 判断是 b64 还是 url：b64 不含 http 且较长，或以 data: 开头
                if (firstUrl.startsWith("data:") || (!firstUrl.startsWith("http") && firstUrl.length() > 200)) {
                    ossUrl = uploadB64ToOss(firstUrl, task.getType(), task.getId());
                } else {
                    ossUrl = uploadToOss(firstUrl, task.getType(), task.getId());
                }
            } else if (result.b64Json() != null) {
                ossUrl = uploadB64ToOss(result.b64Json(), task.getType(), task.getId());
            } else {
                throw new IllegalStateException("图片生成结果为空");
            }
            task.setResultUrl(ossUrl);
            task.setOssUrl(ossUrl);
            task.setStatus(STATUS_SUCCESS);
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
            long groupId = saveToMediaAsset(task, ossUrl, displayPrompt, sizePreset, w, h);

            // 多图：从第二张起额外写入素材库，共享同一素材组
            var extraUrls = result.urls();
            if (extraUrls != null && extraUrls.size() > 1) {
                for (int i = 1; i < extraUrls.size(); i++) {
                    try {
                        String extra =
                                uploadToOss(
                                        extraUrls.get(i), task.getType(), task.getId() * 1000L + i);
                        saveExtraAsset(task, extra, sizePreset, w, h, groupId);
                    } catch (Exception e) {
                        log.warn("[submitSync] 第{}张图上传失败: taskId={}", i + 1, taskId, e);
                    }
                }
            }

            log.info("[submitSync] 任务完成: taskId={}, ossUrl={}", taskId, ossUrl);
        } catch (Exception e) {
            log.error("[submitSync] 生成失败: taskId={}", taskId, e);
            task.setStatus(STATUS_FAIL);
            task.setErrorMsg(e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            taskRepo.save(task);
        }
        try {
            eventService.push(
                    task.getUserId(),
                    task.getStatus().equals(STATUS_SUCCESS) ? EVENT_COMPLETED : EVENT_FAILED,
                    toVO(task));
        } catch (Exception e) {
            log.debug("[submitSync] SSE 推送失败（连接已断开）: taskId={}", taskId);
        }
    }

    private String uploadToOss(String url, String type, Long taskId) {
        try {
            // 解析 URL 路径部分取扩展名，签名参数不干扰；取不到则按类型降级
            String ext = "png";
            try {
                String path = new java.net.URI(url).getPath();
                int dot = path.lastIndexOf('.');
                String e = (dot >= 0) ? path.substring(dot + 1) : "";
                if (!e.isEmpty() && e.length() <= 5) ext = e;
            } catch (Exception ignore) {}
            // UUID 保证唯一，不依赖 taskId 或 URL 中的任何信息
            String filename = "aigc/%s/%s.%s".formatted(type.toLowerCase(), java.util.UUID.randomUUID(), ext);
            var input = new java.net.URL(url).openStream();
            String key = storageService.upload(input, filename, "image/" + ext);
            return storageService.getUrl(key);
        } catch (Exception e) {
            log.warn("[uploadToOss] 上传失败: url={}", url, e);
            return url;
        }
    }

    private String uploadB64ToOss(String b64, String type, Long taskId) {
        try {
            // 剥离 data URL 前缀：data:image/png;base64,xxx
            String mime = "image/png";
            String ext = "png";
            String data = b64;
            if (b64 != null && b64.startsWith("data:")) {
                int comma = b64.indexOf(',');
                if (comma > 0) {
                    String header = b64.substring(5, comma); // image/png;base64
                    mime = header.contains(";") ? header.substring(0, header.indexOf(';')) : header;
                    ext = mime.contains("/") ? mime.substring(mime.indexOf('/') + 1) : "png";
                    if (ext.equals("jpeg")) ext = "jpg";
                    data = b64.substring(comma + 1);
                }
            }
            byte[] bytes = java.util.Base64.getDecoder().decode(data);
            String filename = "aigc/%s/%d.%s".formatted(type.toLowerCase(), taskId, ext);
            String key =
                    storageService.upload(new java.io.ByteArrayInputStream(bytes), filename, mime);
            return storageService.getUrl(key);
        } catch (Exception e) {
            log.warn("[uploadB64ToOss] 上传失败: taskId={}", taskId, e);
            return "data:image/png;base64," + b64;
        }
    }

    private long saveToMediaAsset(
            AigcTask task, String ossUrl, String displayPrompt, String sizePreset, int w, int h) {
        try {
            var type =
                    switch (task.getType()) {
                        case "VIDEO" -> MediaAssetType.VIDEO;
                        case "MODEL_3D" -> MediaAssetType.MODEL_3D;
                        default -> MediaAssetType.IMAGE;
                    };

            // 每次生成任务创建一个素材组，用 displayPrompt（用户原始输入）命名，避免带入项目提示词前缀
            var group = new MediaAssetGroup();
            String nameSource =
                    displayPrompt != null && !displayPrompt.isBlank()
                            ? displayPrompt
                            : (task.getPrompt() != null && !task.getPrompt().isBlank()
                                    ? task.getPrompt()
                                    : "AI生成-" + task.getType() + "-" + task.getId());
            String groupName = nameSource.substring(0, Math.min(nameSource.length(), 40));
            group.setName(groupName);
            group.setCoverUrl(ossUrl);
            group.setAssetCount(1);
            group.setUserId(task.getUserId());
            group = groupRepository.save(group);

            var dto =
                    new SaveFromGenerationDTO(
                            groupName,
                            type,
                            ossUrl,
                            null,
                            "{\"prompt\":\"%s\",\"model\":\"%s\",\"sizePreset\":\"%s\"}"
                                    .formatted(
                                            task.getPrompt() != null
                                                    ? task.getPrompt().replace("\"", "'")
                                                    : "",
                                            task.getModel() != null ? task.getModel() : "",
                                            sizePreset != null ? sizePreset : ""),
                            w,
                            h,
                            null,
                            group.getId(),
                            true,
                            task.getModelName(),
                            task.getProvider());
            mediaAssetService.saveFromGeneration(task.getUserId(), dto);
            return group.getId();
        } catch (Exception e) {
            log.warn("[saveToMediaAsset] 写入素材库失败: taskId={}", task.getId(), e);
            return 0L;
        }
    }

    /** 多图时追加第 2～N 张到同一素材组 */
    private void saveExtraAsset(
            AigcTask task, String ossUrl, String sizePreset, int w, int h, long groupId) {
        try {
            var type =
                    switch (task.getType()) {
                        case "VIDEO" -> MediaAssetType.VIDEO;
                        case "MODEL_3D" -> MediaAssetType.MODEL_3D;
                        default -> MediaAssetType.IMAGE;
                    };
            String name =
                    task.getPrompt() != null
                            ? task.getPrompt().substring(0, Math.min(task.getPrompt().length(), 40))
                            : "AI生成";
            var dto =
                    new SaveFromGenerationDTO(
                            name,
                            type,
                            ossUrl,
                            null,
                            "{\"prompt\":\"%s\",\"model\":\"%s\",\"sizePreset\":\"%s\"}"
                                    .formatted(
                                            task.getPrompt() != null
                                                    ? task.getPrompt().replace("\"", "'")
                                                    : "",
                                            task.getModel() != null ? task.getModel() : "",
                                            sizePreset != null ? sizePreset : ""),
                            w,
                            h,
                            null,
                            groupId,
                            true,
                            task.getModelName(),
                            task.getProvider());
            mediaAssetService.saveFromGeneration(task.getUserId(), dto);
        } catch (Exception e) {
            log.warn("[saveExtraAsset] 追加素材失败: taskId={}", task.getId(), e);
        }
    }

    private com.xuejiai.aaf.module.ai.aigc.task.AigcTaskVO toVO(AigcTask task) {
        return new AigcTaskVO(
                task.getId(),
                task.getUserId(),
                task.getType(),
                task.getStatus(),
                task.getProvider(),
                task.getModel(),
                task.getPrompt(),
                task.getTaskId(),
                task.getResultUrl(),
                task.getOssUrl(),
                task.getErrorMsg(),
                task.getCreateTime(),
                task.getUpdateTime());
    }
}
