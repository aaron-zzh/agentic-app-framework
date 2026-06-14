package com.xuejiai.aaf.module.system.contact.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 联系人渠道身份。
 *
 * <p>记录同一个人在不同平台的外部 ID：企微 userId、钉钉 userId、微信 openId 等。 一个 Contact 可以有多条 ContactIdentity（一人多渠道）。
 *
 * <p>与 sys_user_oauth 的分工：
 *
 * <ul>
 *   <li>sys_contact_identity：身份索引（who are you on each platform），无需有系统账号
 *   <li>sys_user_oauth：OAuth 登录凭证（access_token/refresh_token），必须有系统账号
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_contact_identity",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_sys_contact_identity_channel_external",
                        columnNames = {"channel", "external_id", "corp_id"}))
@SQLDelete(
        sql =
                "UPDATE sys_contact_identity SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ContactIdentity extends BaseEntity {

    /** 关联联系人 ID */
    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    /**
     * 渠道标识：WECOM / DINGTALK / WECHAT_MP / WECHAT_MINI 等。
     *
     * <p>复用 ChannelTypeEnum 名称，但存 VARCHAR 避免跨模块强依赖。
     */
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    /** 渠道内的外部用户 ID（企微 userId、钉钉 userId、微信 openId 等） */
    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    /** 企业 ID（企微/钉钉需要区分企业，微信类填 null） */
    @Column(name = "corp_id", length = 100)
    private String corpId;

    /** 渠道内显示名称 */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** 渠道内头像 URL */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
}
