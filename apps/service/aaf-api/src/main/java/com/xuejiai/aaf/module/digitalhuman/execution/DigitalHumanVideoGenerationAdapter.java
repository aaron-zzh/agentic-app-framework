package com.xuejiai.aaf.module.digitalhuman.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcToolExecutionPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数字人视频生成执行器适配器
 *
 * <p>根据视频时长自动路由到不同的生成服务：
 * <ul>
 * <li>≤15秒：使用阿里云数字人视频生成API（单场景）</li>
 * <li>>15秒：使用Hyperframes多场景视频合成API</li>
 * </ul>
 *
 * @author Kiro
 */
@Slf4j
@Component("digital_human_video_generation")
@RequiredArgsConstructor
public class DigitalHumanVideoGenerationAdapter implements AigcToolExecutionPort {

    private static final int ALIYUN_MAX_DURATION = 15;

    @Override
    public ToolResult execute(Command command) {
        try {
            // 解析输入参数
            Long digitalHumanId = getLong(command.input(), "digitalHumanId");
            String avatarImageUrl = getString(command.input(), "avatarImageUrl");
            String scriptContent = getString(command.input(), "scriptContent");
            Integer duration = getInteger(command.input(), "duration");
            String aspectRatio = getString(command.input(), "aspectRatio", "16:9");
            Map<String, Object> voiceConfig = getMap(command.input(), "voiceConfig");

            // 验证必填参数
            if (digitalHumanId == null) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "digitalHumanId is required");
            }
            if (avatarImageUrl == null || avatarImageUrl.isBlank()) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "avatarImageUrl is required");
            }
            if (scriptContent == null || scriptContent.isBlank()) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "scriptContent is required");
            }
            if (duration == null || duration <= 0) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "duration must be positive");
            }

            // 根据时长选择生成方式
            String videoUrl;
            String provider;

            if (duration <= ALIYUN_MAX_DURATION) {
                log.info("使用阿里云简单视频生成，时长: {}秒", duration);
                videoUrl = generateAliyunVideo(avatarImageUrl, scriptContent, duration, aspectRatio, voiceConfig);
                provider = "aliyun";
            } else {
                log.info("使用Hyperframes多场景合成，时长: {}秒", duration);
                // 将长脚本拆分为多个场景
                List<Map<String, Object>> scenes = splitScriptIntoScenes(scriptContent, duration);
                videoUrl = generateHyperframesVideo(avatarImageUrl, scenes, aspectRatio, voiceConfig);
                provider = "hyperframes";
            }

            // 构建返回结果
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("videoUrl", videoUrl);
            output.put("duration", duration);
            output.put("provider", provider);
            output.put("aspectRatio", aspectRatio);

            return new ToolResult(toJson(output), List.of());

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("数字人视频生成失败", e);
            throw new BusinessException(GlobalErrorCode.INTERNAL_ERROR, "视频生成失败: " + e.getMessage());
        }
    }

    /**
     * 调用阿里云数字人视频生成API（单场景，≤15秒）
     */
    private String generateAliyunVideo(
            String avatarImageUrl,
            String scriptContent,
            int duration,
            String aspectRatio,
            Map<String, Object> voiceConfig) {

        // TODO: 实际API调用逻辑
        // 1. 准备请求参数
        // 2. 调用阿里云数字人视频生成API
        // 3. 轮询任务状态直到完成
        // 4. 返回视频URL

        log.info("调用阿里云视频生成API - avatarUrl: {}, duration: {}s", avatarImageUrl, duration);

        // 临时返回mock URL
        return "https://example.aliyun.com/videos/mock-" + System.currentTimeMillis() + ".mp4";
    }

    /**
     * 调用Hyperframes多场景视频合成API（>15秒）
     */
    private String generateHyperframesVideo(
            String avatarImageUrl,
            List<Map<String, Object>> scenes,
            String aspectRatio,
            Map<String, Object> voiceConfig) {

        // TODO: 实际API调用逻辑
        // 1. 为每个场景生成视频片段
        // 2. 调用Hyperframes合成API
        // 3. 等待合成完成
        // 4. 返回最终视频URL

        log.info("调用Hyperframes合成API - avatarUrl: {}, sceneCount: {}", avatarImageUrl, scenes.size());

        // 临时返回mock URL
        return "https://example.hyperframes.ai/videos/mock-" + System.currentTimeMillis() + ".mp4";
    }

    /**
     * 将长脚本智能拆分为多个场景
     *
     * <p>拆分策略：
     * <ul>
     * <li>按句号、问号、感叹号等标点符号拆分</li>
     * <li>每个场景不超过15秒</li>
     * <li>尽量保持语义完整</li>
     * </ul>
     */
    private List<Map<String, Object>> splitScriptIntoScenes(String scriptContent, int totalDuration) {
        // TODO: 实现智能拆分逻辑
        // 1. 按标点符号拆分句子
        // 2. 估算每句话的时长（按字符数 / 语速）
        // 3. 组合成多个≤15秒的场景
        // 4. 分配每个场景的具体时长

        // 简单实现：按15秒均分
        List<Map<String, Object>> scenes = new java.util.ArrayList<>();
        int sceneCount = (totalDuration + ALIYUN_MAX_DURATION - 1) / ALIYUN_MAX_DURATION;
        int avgDuration = totalDuration / sceneCount;

        String[] sentences = scriptContent.split("[。！？]");
        int sentencesPerScene = Math.max(1, sentences.length / sceneCount);

        for (int i = 0; i < sceneCount; i++) {
            int startIdx = i * sentencesPerScene;
            int endIdx = Math.min(startIdx + sentencesPerScene, sentences.length);

            String sceneContent = String.join("。",
                java.util.Arrays.copyOfRange(sentences, startIdx, endIdx));

            Map<String, Object> scene = new LinkedHashMap<>();
            scene.put("order", i + 1);
            scene.put("title", "场景 " + (i + 1));
            scene.put("content", sceneContent);
            scene.put("duration", i == sceneCount - 1 ? totalDuration - avgDuration * i : avgDuration);

            scenes.add(scene);
        }

        log.info("脚本拆分完成，共{}个场景", scenes.size());
        return scenes;
    }

    // 辅助方法

    private String getString(Map<String, Object> map, String key) {
        return getString(map, key, null);
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<>();
    }

    private String toJson(Map<String, Object> map) {
        try {
            return com.xuejiai.aaf.common.util.JsonUtils.toJsonString(map);
        } catch (Exception e) {
            log.error("JSON序列化失败", e);
            return "{}";
        }
    }
}
