package com.xuejiai.aaf.module.elder.rehab.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.elder.rehab.domain.RehabGuide;
import com.xuejiai.aaf.module.elder.rehab.vo.RehabGuideCreateDTO;

/** 康复指导创建对象转换器。 */
@Mapper
public interface RehabGuideConvert {

    RehabGuideConvert INSTANCE = Mappers.getMapper(RehabGuideConvert.class);

    RehabGuide toEntity(RehabGuideCreateDTO dto);
}
