package com.xuejiai.aaf.module.ai.aigc.configuration.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcProjectType;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcProjectTypeCreateDTO;

/**
 * 项目类型对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface AigcProjectTypeConvert {

    AigcProjectTypeConvert INSTANCE = Mappers.getMapper(AigcProjectTypeConvert.class);

    AigcProjectType toEntity(AigcProjectTypeCreateDTO dto);
}
