package com.xuejiai.aaf.module.elder.profile.event;

import java.time.Instant;

/** 用药计划停用事件，仅携带跨域撤销提醒所需标识。 */
public record MedicationPlanDeactivatedEvent(Long planId, Long elderId, Instant deactivatedAt) {}
