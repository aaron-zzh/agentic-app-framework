package com.xuejiai.aaf.module.ai.aigc.event.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.messaging.event.SysEventPublisher;
import com.xuejiai.aaf.module.ai.aigc.task.api.AigcTaskView;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/**
 * AIGC 事件适配：把 VO / View 转为全局事件的 {@code refs / payload}，类型加 {@code aigc.} 前缀后交给 {@link
 * SysEventPublisher}。框架层不依赖任何 AIGC 类型。
 */
@Service
@RequiredArgsConstructor
public class AigcEventPublisher {

    private static final String TYPE_PREFIX = "aigc.";

    private final SysEventPublisher publisher;

    /** 发布携带显式关联的事件，{@code eventType} 为不带域前缀的类型，如 {@code task.completed}。 */
    public void publish(
            Long userId,
            String eventType,
            Long projectId,
            Long executionRunId,
            Long taskId,
            Long objectVersionId,
            Long reviewId,
            Long workId,
            Long publicationId,
            Object data) {
        var refs = new LinkedHashMap<String, Long>();
        refs.put("projectId", projectId);
        refs.put("executionRunId", executionRunId);
        refs.put("taskId", taskId);
        refs.put("objectVersionId", objectVersionId);
        refs.put("reviewId", reviewId);
        refs.put("workId", workId);
        refs.put("publicationId", publicationId);
        publisher.publish(userId, prefixed(eventType), refs, payload(data));
    }

    /**
     * 发布自定义关联键的事件（如 {@code scheduleId}、{@code proposalId}），{@code eventType} 为不带域前缀的类型。
     *
     * <p>{@code refs} 值须为正整数 ID，字符串类关联放 {@code payload}。
     */
    public void publish(
            Long userId, String eventType, Map<String, Long> refs, Map<String, Object> payload) {
        publisher.publish(userId, prefixed(eventType), refs, payload);
    }

    /** 发布任务类事件：关联从 {@link AigcTaskVO} / {@link AigcTaskView} 提取，其他数据只作 payload。 */
    public void publish(Long userId, String eventType, Object data) {
        var refs = new LinkedHashMap<String, Long>();
        if (data instanceof AigcTaskVO task) {
            refs.put("taskId", task.id());
            refs.put("projectId", task.projectId());
            refs.put("executionRunId", task.executionRunId());
            refs.put("mediaVersionId", task.outputMediaVersionId());
        } else if (data instanceof AigcTaskView task) {
            refs.put("taskId", task.id());
            refs.put("executionRunId", task.executionRunId());
            refs.put("mediaVersionId", task.outputMediaVersionId());
        }
        publisher.publish(userId, prefixed(eventType), refs, payload(data));
    }

    private static String prefixed(String eventType) {
        return eventType.startsWith(TYPE_PREFIX) ? eventType : TYPE_PREFIX + eventType;
    }

    /** 事件 payload 仅作变化通知；任务类数据只保留状态摘要，详情由前端按 taskId 查询。 */
    private Map<String, Object> payload(Object data) {
        if (data == null) {
            return Map.of();
        }
        if (data instanceof AigcTaskVO task) {
            return summary("type", task.type(), "status", task.status());
        }
        if (data instanceof AigcTaskView task) {
            return summary(
                    "type",
                    task.taskType(),
                    "status",
                    task.status(),
                    "failureCode",
                    task.failureCode());
        }
        var json = JsonUtils.toJsonString(data);
        try {
            var parsed = JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {});
            var result = new LinkedHashMap<String, Object>(parsed);
            result.values().removeIf(Objects::isNull);
            return result;
        } catch (RuntimeException error) {
            return Map.of("data", String.valueOf(data));
        }
    }

    /** 按键值对构造摘要 payload，忽略 null 值。 */
    private static Map<String, Object> summary(Object... keyValues) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                result.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return result;
    }
}
