package com.xuejiai.aaf.module.elder.profile.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.xuejiai.aaf.common.enums.elder.MedicationPlanSourceEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationPlanStatusEnum;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.framework.crud.reference.CrudReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 长者用药提醒计划。 */
@Getter
@Setter
@Entity
@Table(name = "elder_medication_plan")
@SQLDelete(
        sql =
                "UPDATE elder_medication_plan SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class MedicationPlan extends BaseEntity {

    /** 长者 ID。 */
    @CrudReference(targetResource = "elder.profile.elder")
    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    /** 药品名称，由人提供。 */
    @Column(name = "medication_name", nullable = false, length = 200)
    private String medicationName;

    /** 剂量说明，由人提供。 */
    @Column(name = "dosage_instructions", nullable = false, length = 500)
    private String dosageInstructions;

    /** 每日提醒时间点，格式为 HH:mm 字符串数组。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reminder_times", nullable = false, columnDefinition = "jsonb")
    private String reminderTimes = "[]";

    /** 生效日期。 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 结束日期，可为空。 */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 计划状态。枚举 {@link MedicationPlanStatusEnum}。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = MedicationPlanStatusEnum.ACTIVE.getCode();

    /** 创建来源。枚举 {@link MedicationPlanSourceEnum}。 */
    @Column(name = "source", nullable = false, length = 16)
    private String source = MedicationPlanSourceEnum.MANAGEMENT.getCode();

    /** 下一提醒时间，供定时扫描使用。 */
    @Column(name = "next_reminder_at")
    private LocalDateTime nextReminderAt;
}
