package com.xuejiai.aaf.module.system.contact.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.sys.ContactSourceEnum;
import com.xuejiai.aaf.common.enums.sys.ContactStatusEnum;
import com.xuejiai.aaf.common.enums.sys.ContactTypeEnum;
import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 联系人/伙伴。
 *
 * <p>所有"人/组织"的统一身份本体。客户线索、渠道关注者、访客均为 contact。不一定能登录。
 *
 * <p>能登录的 contact 通过 sys_user.contact_id 关联到系统账号。 渠道身份（企微userId、微信openId等）存于 sys_contact_identity。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_contact")
@SQLDelete(
        sql = "UPDATE sys_contact SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Contact extends BaseEntity {

    /** 显示名称 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 真实姓名（可空，实名认证后填写） */
    @Column(name = "real_name", length = 100)
    private String realName;

    /** 手机号 */
    @Column(name = "phone", length = 20)
    private String phone;

    /** 邮箱 */
    @Column(name = "email", length = 200)
    private String email;

    /** 头像 URL */
    @Column(name = "avatar", length = 500)
    private String avatar;

    /** 联系人类型：PERSON=个人 / ORG=组织 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ContactTypeEnum type = ContactTypeEnum.PERSON;

    /** 来源：REGISTER / IMPORT / CHANNEL / VISITOR */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private ContactSourceEnum source;

    /** 状态：ACTIVE / LEAD / VISITOR / ARCHIVED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ContactStatusEnum status = ContactStatusEnum.ACTIVE;

    /** 所属组织（自关联，type=ORG 时为父组织） */
    @Column(name = "parent_id")
    private Long parentId;

    /** 扩展字段（地区、标签、行业等） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ext", columnDefinition = "jsonb")
    private String ext;
}
