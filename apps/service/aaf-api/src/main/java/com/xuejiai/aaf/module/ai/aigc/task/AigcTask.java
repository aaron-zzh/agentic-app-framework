package com.xuejiai.aaf.module.ai.aigc.task;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * AIGC 统一任务实体——IMAGE / VIDEO / MODEL_3D 三类任务的统一存储。
 *
 * @author Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "aigc_task")
public class AigcTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 任务类型：IMAGE / VIDEO / MODEL_3D */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 任务状态：PENDING / RUNNING / SUCCESS / FAIL */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    /** 提供商（wanx / midjourney / ...） */
    @Column(name = "provider", length = 50)
    private String provider;

    /** 模型名称 */
    @Column(name = "model", length = 100)
    private String model;

    /** 生成 prompt */
    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    /** 额外参数，JSON 格式（width/height 等） */
    @Column(name = "params", columnDefinition = "JSONB")
    private String params;

    /** 第三方任务 ID */
    @Column(name = "task_id", length = 200)
    private String taskId;

    /** 第三方结果 URL（未上传 OSS 前） */
    @Column(name = "result_url", columnDefinition = "TEXT")
    private String resultUrl;

    /** OSS 存储 URL（上传后写入） */
    @Column(name = "oss_url", columnDefinition = "TEXT")
    private String ossUrl;

    /** 失败原因 */
    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "create_time")
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time")
    private LocalDateTime updateTime = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
