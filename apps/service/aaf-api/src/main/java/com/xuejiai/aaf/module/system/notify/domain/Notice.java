package com.xuejiai.aaf.module.system.notify.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 通知公告。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_notice")
@SQLDelete(
        sql =
                "UPDATE sys_notice SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Notice extends BaseEntity {

    /** 标题 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 内容 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 类型：NOTICE=通知 ANNOUNCEMENT=公告 */
    @Column(nullable = false, length = 20)
    private String type;

    /** 状态：0=草稿 1=已发布 */
    @Column(nullable = false)
    private Short status = 0;

    /** 发布时间 */
    private LocalDateTime publishTime;
}
