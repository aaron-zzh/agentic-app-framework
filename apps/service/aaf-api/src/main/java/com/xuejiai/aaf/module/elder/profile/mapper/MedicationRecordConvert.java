package com.xuejiai.aaf.module.elder.profile.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.elder.profile.domain.MedicationRecord;
import com.xuejiai.aaf.module.elder.profile.vo.MedicationRecordCreateDTO;

/** 服药事实创建对象转换器；长者和默认记录时间由 Service 设置。 */
@Mapper
public interface MedicationRecordConvert {

    MedicationRecordConvert INSTANCE = Mappers.getMapper(MedicationRecordConvert.class);

    @Mapping(target = "elderId", ignore = true)
    MedicationRecord toEntity(MedicationRecordCreateDTO dto);
}
