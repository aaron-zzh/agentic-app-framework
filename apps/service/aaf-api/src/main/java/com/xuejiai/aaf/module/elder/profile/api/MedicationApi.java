package com.xuejiai.aaf.module.elder.profile.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 用药跨子域契约；guard 只通过此接口读取到点计划和写入服药结果。 */
public interface MedicationApi {

    /** 查询半开时间窗 {@code [windowStart, windowEnd)} 内到点且已生效的计划。 */
    List<DueMedicationPlan> listDuePlans(Instant windowStart, Instant windowEnd, ZoneId zoneId);

    /** 按计划与计划时间幂等写入服药事实，重复调用返回首次记录。 */
    MedicationRecordSnapshot recordResult(RecordMedicationResult command);

    /** 从最近一条记录开始统计同一计划连续漏服次数。 */
    int countConsecutiveMissed(Long planId);

    /** 查询已授权长者的用药计划及当天服药事实。 */
    List<MedicationPlanSnapshot> listPlans(Long elderId, LocalDate date);

    /** 通过已确认的对话草稿创建立即生效的计划，并通知联系人核对。 */
    MedicationPlanSnapshot createConversationPlan(CreateConversationPlan command);

    record DueMedicationPlan(
            Long planId,
            Long elderId,
            String medicationName,
            String dosageInstructions,
            Instant scheduledAt,
            ZoneId zoneId) {}

    record CreateConversationPlan(
            Long elderId,
            String medicationName,
            String dosageInstructions,
            List<String> reminderTimes,
            LocalDate startDate,
            LocalDate endDate) {}

    record MedicationPlanSnapshot(
            Long id,
            String medicationName,
            String dosageInstructions,
            List<String> reminderTimes,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String source,
            List<MedicationResultSnapshot> results) {}

    record MedicationResultSnapshot(
            String scheduledAt, String result, String source, String recordedAt) {}

    record RecordMedicationResult(
            Long planId,
            Instant scheduledAt,
            ZoneId zoneId,
            String result,
            String source,
            Instant recordedAt,
            String sourceReference) {}

    record MedicationRecordSnapshot(
            Long id,
            Long planId,
            Long elderId,
            Instant scheduledAt,
            ZoneId zoneId,
            String result,
            String source,
            Instant recordedAt,
            String sourceReference) {}
}
