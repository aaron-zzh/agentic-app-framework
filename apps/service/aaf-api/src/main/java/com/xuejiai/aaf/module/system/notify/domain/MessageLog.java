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
 * 统一消息发送日志（EMAIL/SMS/DINGTALK 等所有渠道）。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "sys_message_log")
@SQLDelete(
        sql =
                "UPDATE sys_message_log SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class MessageLog extends BaseEntity {

    /** 消息渠道（EMAIL/SMS/DINGTALK） */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /** 模板编码 */
    @Column(name = "template_code", length = 100)
    private String templateCode;

    /** 收件人（JSON 数组字符串） */
    @Column(name = "recipients", columnDefinition = "TEXT")
    private String recipients;

    /** 消息主题 */
    @Column(name = "subject", length = 200)
    private String subject;

    /** 渲染后的消息内容 */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 发送状态：PENDING=待发送 SUCCESS=成功 FAILED=失败 */
    @Column(name = "status", nullable = false, length = 10)
    private String status = "PENDING";

    /** 错误信息 */
    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    /** 实际发送时间 */
    @Column(name = "send_time")
    private LocalDateTime sendTime;
}
