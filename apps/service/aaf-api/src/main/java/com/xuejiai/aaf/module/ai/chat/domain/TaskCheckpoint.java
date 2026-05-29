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

/**
 * 任务检查点快照——支持从最近检查点恢复执行。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_task_checkpoint")
public class TaskCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    /** coordinator / subtask / agent_step */
    @Column(name = "scope", nullable = false, length = 20)
    private String scope;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex = 0;

    /** 状态快照 JSON */
    @Column(name = "state_json", nullable = false, columnDefinition = "JSONB")
    private String stateJson;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();
}
