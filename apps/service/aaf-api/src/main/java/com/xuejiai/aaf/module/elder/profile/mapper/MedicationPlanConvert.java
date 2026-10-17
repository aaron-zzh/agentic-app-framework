package com.xuejiai.aaf.module.elder.profile.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.common.enums.elder.MedicationPlanSourceEnum;
import com.xuejiai.aaf.common.enums.elder.MedicationPlanStatusEnum;
import com.xuejiai.aaf.module.elder.profile.domain.MedicationPlan;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationPlanCreateDTO;

/** 用药计划创建对象转换器；提醒时间和下一提醒时间由 Service 处理。 */
@Mapper(imports = {MedicationPlanStatusEnum.class, MedicationPlanSourceEnum.class})
public interface MedicationPlanConvert {

    MedicationPlanConvert INSTANCE = Mappers.getMapper(MedicationPlanConvert.class);

    @Mapping(target = "reminderTimes", ignore = true)
    @Mapping(target = "nextReminderAt", ignore = true)
    @Mapping(
            target = "status",
            source = "status",
            defaultExpression = "java(MedicationPlanStatusEnum.ACTIVE.getCode())")
    @Mapping(
            target = "source",
            source = "source",
            defaultExpression = "java(MedicationPlanSourceEnum.MANAGEMENT.getCode())")
    MedicationPlan toEntity(MedicationPlanCreateDTO dto);
}
