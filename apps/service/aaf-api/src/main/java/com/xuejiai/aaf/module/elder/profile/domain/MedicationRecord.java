package com.xuejiai.aaf.module.elder.profile.domain;

import java.time.LocalDateTime;

import com.xuejiai.aaf.common.enums.elder.MedicationRecordResultEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationRecordSourceEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 每个用药提醒时间点的服药事实，只允许新增。 */
@Getter
@Setter
@Entity
@Table(name = "elder_medication_record")
public class MedicationRecord extends BaseEntity {

    /** 长者 ID。 */
    @CrudReference(targetResource = "elder.profile.elder")
    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    /** 用药计划 ID。 */
    @CrudReference(targetResource = "elder.profile.medication-plan")
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 计划服药时间。 */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** 服药结果。枚举 {@link MedicationRecordResultEnum}。 */
    @Column(name = "result", nullable = false, length = 16)
    private String result;

    /** 记录来源。枚举 {@link MedicationRecordSourceEnum}。 */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    /** 实际确认或判定时间。 */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    /** 来源记录引用，例如设备消息 commandId。 */
    @Column(name = "source_reference", length = 100)
    private String sourceReference;
}
