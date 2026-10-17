package com.xuejiai.aaf.module.elder.profile.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.elder.ContactRelationshipEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 长者联系人与家属授权。 */
@Getter
@Setter
@Entity
@Table(name = "elder_contact")
@CrudReference(
        key = "createBy",
        idProperty = "createBy",
        targetResource = "system.user",
        viewField = "createBy",
        capabilities = ReferenceCapability.READ)
@CrudReference(
        key = "updateBy",
        idProperty = "updateBy",
        targetResource = "system.user",
        viewField = "updateBy",
        capabilities = ReferenceCapability.READ)
@SQLDelete(
        sql =
                "UPDATE elder_contact SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class ElderContact extends BaseEntity {

    /** 长者 ID。 */
    @CrudReference(targetResource = "elder.profile.elder")
    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    /** 已绑定的 AAF 用户，可为空。 */
    @CrudReference(
            targetResource = "system.user",
            additionalPolicyBean = "elderUserReferencePolicy")
    @Column(name = "user_id")
    private Long userId;

    /** 联系人姓名。 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 联系电话。 */
    @Column(name = "mobile", nullable = false, length = 32)
    private String mobile;

    /** 与长者关系。枚举 {@link ContactRelationshipEnum}。 */
    @Column(name = "relationship", nullable = false, length = 32)
    private String relationship;

    /** 联系优先级，数值越小越优先。 */
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    /** 是否紧急联系人。 */
    @Column(name = "emergency_contact", nullable = false)
    private Boolean emergencyContact = true;

    /** 授权范围编码数组。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authorization_scopes", nullable = false, columnDefinition = "jsonb")
    private String authorizationScopes = "[]";
}
