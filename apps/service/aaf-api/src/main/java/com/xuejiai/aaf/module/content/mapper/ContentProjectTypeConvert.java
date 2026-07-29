package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentProjectType;
import com.xuejiai.aaf.module.content.vo.ContentProjectTypeCreateDTO;

/**
 * 项目类型对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentProjectTypeConvert {

    ContentProjectTypeConvert INSTANCE = Mappers.getMapper(ContentProjectTypeConvert.class);

    ContentProjectType toEntity(ContentProjectTypeCreateDTO dto);
}
