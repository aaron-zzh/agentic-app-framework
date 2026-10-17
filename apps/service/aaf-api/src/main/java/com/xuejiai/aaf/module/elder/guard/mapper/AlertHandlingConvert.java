package com.xuejiai.aaf.module.elder.guard.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.elder.guard.domain.AlertHandling;
import com.xuejiai.aaf.module.elder.guard.vo.AlertHandlingCreateDTO;

/** 预警处理创建对象转换器。 */
@Mapper
public interface AlertHandlingConvert {

    AlertHandlingConvert INSTANCE = Mappers.getMapper(AlertHandlingConvert.class);

    AlertHandling toEntity(AlertHandlingCreateDTO dto);
}
