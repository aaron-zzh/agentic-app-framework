package com.xuejiai.aaf.module.ai.chat.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 任务事件日志（append-only）——完整审计轨迹。 */
@Getter
@Setter
@Entity
@Table(name = "ai_task_event")
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "subtask_key", length = 100)
    private String subtaskKey;

    /** 事件类型 */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    /** 事件载荷 JSON */
    @Column(name = "payload_json", columnDefinition = "JSONB")
    private String payloadJson;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    public static TaskEvent of(Long taskId, Long executionId, String type, String payload) {
        var e = new TaskEvent();
        e.setTaskId(taskId);
        e.setExecutionId(executionId);
        e.setType(type);
        e.setPayloadJson(payload);
        return e;
    }

    public static TaskEvent of(
            Long taskId, Long executionId, String subtaskKey, String type, String payload) {
        var e = of(taskId, executionId, type, payload);
        e.setSubtaskKey(subtaskKey);
        return e;
    }
}
