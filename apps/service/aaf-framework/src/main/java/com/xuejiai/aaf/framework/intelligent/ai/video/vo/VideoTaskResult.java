package com.xuejiai.aaf.framework.intelligent.ai.video.vo;

import java.util.HashMap;
import java.util.Map;

import com.xuejiai.aaf.framework.intelligent.core.AiUsage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 视频任务查询结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoTaskResult implements AiUsage {

    private String taskId;
    private TaskStatus status;
    private String videoUrl;
    private String origPrompt;
    private String submitTime;
    private String endTime;
    private Integer duration;

    /** 实际生成分辨率，如 "720p"/"1080p"，供 PER_UNIT 结算使用。 */
    private String resolution;

    /** 官方返回的错误信息（失败时填充）。 */
    private String errorMessage;

    /** 兼容旧调用（无 resolution / errorMessage）。 */
    public VideoTaskResult(
            String taskId,
            TaskStatus status,
            String videoUrl,
            String origPrompt,
            String submitTime,
            String endTime,
            Integer duration) {
        this(taskId, status, videoUrl, origPrompt, submitTime, endTime, duration, null, null);
    }

    /** 兼容旧调用（有 resolution，无 errorMessage）。 */
    public VideoTaskResult(
            String taskId,
            TaskStatus status,
            String videoUrl,
            String origPrompt,
            String submitTime,
            String endTime,
            Integer duration,
            String resolution) {
        this(taskId, status, videoUrl, origPrompt, submitTime, endTime, duration, resolution, null);
    }

    @Override
    public Map<String, Object> standardUsage() {
        var map = new HashMap<String, Object>();
        map.put("count", 1);
        if (duration != null) map.put("duration", duration);
        if (resolution != null) map.put("resolution", resolution);
        return map;
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELED,
        UNKNOWN
    }
}
