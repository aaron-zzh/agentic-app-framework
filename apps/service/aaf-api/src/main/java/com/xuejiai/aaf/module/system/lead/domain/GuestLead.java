package com.xuejiai.aaf.module.system.lead.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.lead.LeadChannelEnum;
import com.xuejiai.aaf.common.enums.lead.LeadStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 访客线索实体。
 *
 * <p>记录未登录用户在公开页的所有动作流水：匿名对话续聊（CHAT）、邮箱订阅（NEWSLETTER）、 联系我们（CONTACT）、用户反馈（FEEDBACK）。同一访客通过 {@link
 * #anonymousId}（前端 localStorage UUID）关联多次动作。
 *
 * <p>访客转正（注册成正式 {@link com.xuejiai.aaf.module.system.contact.domain.Contact}）后，可填充 {@link
 * #contactId} 建立关联，方便后续做"访客 → 注册用户"转化分析。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "ops_guest_lead")
@SQLDelete(
        sql =
                "UPDATE ops_guest_lead SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class GuestLead extends BaseEntity {

    /** 访客匿名 ID（前端 localStorage 持久 UUID，同一访客的多次动作共用） */
    @Column(name = "anonymous_id", nullable = false, length = 64)
    private String anonymousId;

    /** 动作渠道 */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 32)
    private LeadChannelEnum channel;

    /** 邮箱（NEWSLETTER 必填，CONTACT/FEEDBACK 可选） */
    @Column(name = "email", length = 200)
    private String email;

    /** 姓名/昵称（CONTACT/FEEDBACK 可选） */
    @Column(name = "name", length = 100)
    private String name;

    /** 电话（CONTACT 可选） */
    @Column(name = "phone", length = 50)
    private String phone;

    /** 主题（CONTACT 可选） */
    @Column(name = "subject", length = 200)
    private String subject;

    /** 内容/留言（CONTACT/FEEDBACK 可选） */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** AG-UI threadId（CHAT 渠道续聊用） */
    @Column(name = "thread_id", length = 64)
    private String threadId;

    /** AgentScope 路由角色（CHAT 渠道续聊用） */
    @Column(name = "agent_role", length = 64)
    private String agentRole;

    /** 最后一条消息时间（CHAT 渠道用，便于按"最近活跃"排序续聊） */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** 来源 IP（后端写入，前端不可控） */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    /** User-Agent（后端写入） */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Referer（后端写入） */
    @Column(name = "referer", length = 500)
    private String referer;

    /** IP 推断的归属地（如"广东 深圳市 南山区"），由 IpUtils.getAreaName 填充 */
    @Column(name = "region", length = 100)
    private String region;

    /** 处理状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeadStatusEnum status = LeadStatusEnum.NEW;

    /** 处理人 sys_user.id */
    @Column(name = "handled_by")
    private Long handledBy;

    /** 处理时间 */
    @Column(name = "handled_time")
    private LocalDateTime handledTime;

    /** 访客转正后关联的 sys_contact.id（可空） */
    @Column(name = "contact_id")
    private Long contactId;
}
