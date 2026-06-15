package com.xuejiai.aaf.module.ai.aigc.task.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * AIGC 统一任务实体——IMAGE / VIDEO / MODEL_3D / MUSIC 四类任务的统一存储。
 *
 * @author AaronZZH
 */
@Getter
@Setter
@Entity
@Table(name = "aigc_task")
@SQLDelete(
        sql = "UPDATE aigc_task SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AigcTask extends BaseEntity {

    /** 用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 任务类型：IMAGE / VIDEO / MODEL_3D / MUSIC */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** 任务状态：PENDING / RUNNING / SUCCESS / FAIL */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    /** 提供商（wanx / midjourney / ...） */
    @Column(name = "provider", length = 50)
    private String provider;

    /** 模型 ID */
    @Column(name = "model", length = 100)
    private String model;

    /** 模型显示名称，如 豆包图像生成 */
    @Column(name = "model_name", length = 100)
    private String modelName;

    /** 生成 prompt */
    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    /** 额外参数，JSON 格式（width/height 等） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "JSONB")
    private String params;

    /** 第三方任务 ID */
    @Column(name = "task_id", columnDefinition = "TEXT")
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

    /** 所属项目 ID，NULL 表示全局任务 */
    @Column(name = "project_id")
    private Long projectId;
}
