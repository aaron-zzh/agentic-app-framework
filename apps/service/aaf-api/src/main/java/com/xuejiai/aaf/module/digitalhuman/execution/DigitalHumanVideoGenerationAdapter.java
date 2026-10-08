package com.xuejiai.aaf.module.digitalhuman.execution;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.digitalhuman.ErrorCodeConstants.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcToolExecutionPort;
import com.xuejiai.aaf.module.digitalhuman.domain.DigitalHuman;
import com.xuejiai.aaf.module.digitalhuman.repository.DigitalHumanRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数字人视频生成执行器。
 *
 * <p>绑定到 {@code video.generate} 动作（项目类型 {@code digital_human_video}），由 AIGC Tool 执行器按 {@link
 * #TARGET_REF} 路由到这里。入参来自动作的 {@code actionArguments}：
 *
 * <ul>
 *   <li>{@code digitalHumanId}：数字人 ID，形象与音色由本执行器按 ID 读取，不信任调用方传入的地址
 *   <li>{@code scenes}：脚本产出的场景列表，每项含 {@code content}、{@code duration}，可带 {@code title}、{@code
 *       order}、{@code sceneDescription}；场景已由脚本阶段分好段，本执行器不再拆分
 *   <li>{@code aspectRatio}：画面比例，缺省 16:9
 * </ul>
 *
 * <p>路由规则（唯一一处）：单场景且不超过 {@value #ALIYUN_MAX_DURATION} 秒直接调阿里云出片；其余情况每个场景用阿里云出一段，再交给 Hyperframes 合成。
 *
 * <p>阿里云与 Hyperframes 的实际调用尚未接入，仍返回占位地址。
 *
 * @author Kiro
 */
@Slf4j
@Component("digital_human_video_generation")
@RequiredArgsConstructor
public class DigitalHumanVideoGenerationAdapter implements AigcToolExecutionPort {

    /** 与 {@code aigc_execution_binding.target_ref} 一致。 */
    public static final String TARGET_REF = "digital_human_video_generation";

    /** 阿里云单段视频的最长时长（秒）。 */
    static final int ALIYUN_MAX_DURATION = 15;

    private static final int MAX_SCENES = 50;
    private static final int MAX_TOTAL_DURATION = 600;
    private static final String DEFAULT_ASPECT_RATIO = "16:9";
    private static final Set<String> ASPECT_RATIOS = Set.of("16:9", "9:16", "1:1");

    private static final String PROVIDER_ALIYUN = "aliyun";
    private static final String PROVIDER_HYPERFRAMES = "hyperframes";

    private final DigitalHumanRepository digitalHumanRepository;

    @Override
    public boolean supports(String targetRef) {
        return TARGET_REF.equals(targetRef);
    }

    @Override
    public ToolResult execute(Command command) {
        var arguments = actionArguments(command);
        var digitalHuman =
                requireUsableDigitalHuman(command, longValue(arguments.get("digitalHumanId")));
        var aspectRatio = aspectRatio(arguments.get("aspectRatio"));
        var scenes = parseScenes(arguments.get("scenes"));
        var totalDuration = scenes.stream().mapToInt(Scene::duration).sum();
        if (totalDuration > MAX_TOTAL_DURATION) {
            throw exception(VIDEO_TOTAL_DURATION_EXCEEDED, MAX_TOTAL_DURATION);
        }

        String videoUrl;
        String provider;
        try {
            if (scenes.size() == 1 && totalDuration <= ALIYUN_MAX_DURATION) {
                log.info("数字人视频走阿里云单段，数字人: {}，时长: {}秒", digitalHuman.getId(), totalDuration);
                videoUrl = generateAliyunClip(digitalHuman, scenes.getFirst(), aspectRatio);
                provider = PROVIDER_ALIYUN;
            } else {
                log.info(
                        "数字人视频走多场景合成，数字人: {}，场景数: {}，时长: {}秒",
                        digitalHuman.getId(),
                        scenes.size(),
                        totalDuration);
                var clipUrls = new ArrayList<String>(scenes.size());
                for (var scene : scenes) {
                    clipUrls.add(generateAliyunClip(digitalHuman, scene, aspectRatio));
                }
                videoUrl = composeWithHyperframes(scenes, clipUrls, aspectRatio);
                provider = PROVIDER_HYPERFRAMES;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("数字人视频生成失败，数字人: {}", digitalHuman.getId(), e);
            throw exception(VIDEO_GENERATION_FAILED, e.getMessage());
        }

        var output = new LinkedHashMap<String, Object>();
        output.put("videoUrl", videoUrl);
        output.put("duration", totalDuration);
        output.put("provider", provider);
        output.put("aspectRatio", aspectRatio);
        output.put("sceneCount", scenes.size());
        return new ToolResult(JsonUtils.toJsonString(output), List.of());
    }

    /** 调用阿里云数字人视频生成 API，为单个场景出片（场景时长不超过 15 秒）。 */
    private String generateAliyunClip(DigitalHuman digitalHuman, Scene scene, String aspectRatio) {
        // TODO: 接入阿里云：提交任务、轮询状态、取回视频地址；形象取 digitalHuman.getAvatarImageUrl()，
        // 音色取 digitalHuman.getVoiceConfig()，台词取 scene.content()
        log.info(
                "调用阿里云视频生成（占位）- 数字人: {}, 场景: {}, 时长: {}s, 比例: {}",
                digitalHuman.getId(),
                scene.order(),
                scene.duration(),
                aspectRatio);
        return "https://example.aliyun.com/videos/mock-" + System.currentTimeMillis() + ".mp4";
    }

    /** 调用 Hyperframes 把各场景片段按顺序合成为完整视频。 */
    private String composeWithHyperframes(
            List<Scene> scenes, List<String> clipUrls, String aspectRatio) {
        // TODO: 接入 Hyperframes：按 scenes 顺序提交 clipUrls，等待合成完成并取回视频地址
        log.info("调用 Hyperframes 合成（占位）- 片段数: {}, 比例: {}", clipUrls.size(), aspectRatio);
        return "https://example.hyperframes.ai/videos/mock-" + System.currentTimeMillis() + ".mp4";
    }

    /**
     * 读取并校验数字人。
     *
     * <p>执行器可能运行在没有登录态的调度线程上，因此用 Command 携带的组织与工作空间显式校验，而不依赖线程上下文：组织必须一致，数字人属于某个工作空间时必须与 当前工作空间一致。
     */
    private DigitalHuman requireUsableDigitalHuman(Command command, Long digitalHumanId) {
        if (digitalHumanId == null) {
            throw exception(DIGITAL_HUMAN_ID_REQUIRED);
        }
        var digitalHuman =
                digitalHumanRepository
                        .findById(digitalHumanId)
                        .orElseThrow(() -> exception(DIGITAL_HUMAN_NOT_FOUND));
        var sameOrg = Objects.equals(digitalHuman.getOrgId(), command.orgId());
        var workspaceVisible =
                digitalHuman.getWorkspaceId() == null
                        || Objects.equals(digitalHuman.getWorkspaceId(), command.workspaceId());
        if (!sameOrg || !workspaceVisible) {
            throw exception(DIGITAL_HUMAN_ACCESS_DENIED);
        }
        if (!"active".equals(digitalHuman.getStatus())) {
            throw exception(DIGITAL_HUMAN_ARCHIVED_CANNOT_GENERATE);
        }
        var avatarImageUrl = digitalHuman.getAvatarImageUrl();
        if (avatarImageUrl == null || avatarImageUrl.isBlank()) {
            throw exception(DIGITAL_HUMAN_AVATAR_REQUIRED);
        }
        return digitalHuman;
    }

    private List<Scene> parseScenes(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            throw exception(VIDEO_SCENE_EMPTY);
        }
        if (items.size() > MAX_SCENES) {
            throw exception(VIDEO_SCENE_TOO_MANY, MAX_SCENES);
        }
        var scenes = new ArrayList<Scene>(items.size());
        for (var index = 0; index < items.size(); index++) {
            var order = index + 1;
            if (!(items.get(index) instanceof Map<?, ?> item)) {
                throw exception(VIDEO_SCENE_CONTENT_REQUIRED, order);
            }
            var content = text(item.get("content"));
            if (content == null || content.isBlank()) {
                throw exception(VIDEO_SCENE_CONTENT_REQUIRED, order);
            }
            var duration = intValue(item.get("duration"));
            if (duration == null || duration < 1 || duration > ALIYUN_MAX_DURATION) {
                throw exception(VIDEO_SCENE_DURATION_INVALID, order, ALIYUN_MAX_DURATION);
            }
            scenes.add(
                    new Scene(
                            order,
                            text(item.get("title")),
                            content,
                            duration,
                            text(item.get("sceneDescription"))));
        }
        return List.copyOf(scenes);
    }

    private String aspectRatio(Object value) {
        var ratio = value == null ? DEFAULT_ASPECT_RATIO : String.valueOf(value);
        if (!ASPECT_RATIOS.contains(ratio)) {
            throw exception(VIDEO_ASPECT_RATIO_INVALID, ratio);
        }
        return ratio;
    }

    private Map<?, ?> actionArguments(Command command) {
        return command.input().get("actionArguments") instanceof Map<?, ?> arguments
                ? arguments
                : Map.of();
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Integer intValue(Object value) {
        var parsed = longValue(value);
        return parsed == null || parsed > Integer.MAX_VALUE ? null : parsed.intValue();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 脚本阶段产出的一个场景；{@code order} 为场景在列表中的 1 起始序号。 */
    private record Scene(
            int order, String title, String content, int duration, String sceneDescription) {}
}
