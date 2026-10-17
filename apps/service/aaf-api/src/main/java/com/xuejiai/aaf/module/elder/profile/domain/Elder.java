package com.xuejiai.aaf.module.elder.profile.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.enums.elder.ElderLivingArrangementEnum;
import com.xuejiai.aaf.common.enums.elder.ElderStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;
import com.xuejiai.aaf.framework.crud.reference.ReferenceCapability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 长者档案。 */
@Getter
@Setter
@Entity
@Table(name = "elder_profile")
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
                "UPDATE elder_profile SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Elder extends BaseEntity {

    /** 关联的 AAF 用户，可为空。 */
    @CrudReference(
            targetResource = "system.user",
            additionalPolicyBean = "elderUserReferencePolicy")
    @Column(name = "user_id")
    private Long userId;

    /** 姓名。 */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** 性别，复用字典 sys_user_sex。 */
    @Column(name = "gender", length = 16)
    private String gender;

    /** 出生日期。 */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** 身份证件号。 */
    @Column(name = "id_card_no", length = 64)
    private String idCardNo;

    /** 联系电话。 */
    @Column(name = "mobile", length = 32)
    private String mobile;

    /** 居住地址。 */
    @Column(name = "address", length = 500)
    private String address;

    /** 居住形态。枚举 {@link ElderLivingArrangementEnum}。 */
    @Column(name = "living_arrangement", nullable = false, length = 32)
    private String livingArrangement = ElderLivingArrangementEnum.ALONE.getCode();

    /** 习惯称呼。 */
    @Column(name = "preferred_name", length = 100)
    private String preferredName;

    /** 生活偏好。 */
    @Column(name = "preferences", columnDefinition = "text")
    private String preferences;

    /** 沟通与照护禁忌。 */
    @Column(name = "taboos", columnDefinition = "text")
    private String taboos;

    /** 档案状态。枚举 {@link ElderStatusEnum}。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = ElderStatusEnum.ACTIVE.getCode();

    /** 是否已同意摄像头与预警功能。 */
    @Column(name = "camera_consent", nullable = false)
    private Boolean cameraConsent = false;

    /** 同意人用户 ID。 */
    @CrudReference(
            key = "cameraConsentUser",
            idProperty = "cameraConsentBy",
            targetResource = "system.user",
            inputField = "cameraConsentBy",
            viewField = "cameraConsentUser",
            additionalPolicyBean = "elderUserReferencePolicy")
    @Column(name = "camera_consent_by")
    private Long cameraConsentBy;

    /** 同意时间。 */
    @Column(name = "camera_consent_at")
    private LocalDateTime cameraConsentAt;
}
