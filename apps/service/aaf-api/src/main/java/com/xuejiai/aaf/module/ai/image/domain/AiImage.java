package com.xuejiai.aaf.module.ai.image.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 图像生成记录。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "ai_image")
@SQLDelete(sql = "UPDATE ai_image SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class AiImage extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "platform", nullable = false, length = 30)
    private String platform;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    /** IN_PROGRESS / SUCCESS / FAIL */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "task_id", length = 100)
    private String taskId;

    @Column(name = "pic_url", length = 500)
    private String picUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Midjourney 后续操作按钮，JSON 字符串 */
    @Column(name = "buttons", columnDefinition = "JSONB")
    private String buttons;

    /** 额外参数，JSON 字符串 */
    @Column(name = "options", columnDefinition = "JSONB")
    private String options;

    @Column(name = "finish_time")
    private LocalDateTime finishTime;
}
