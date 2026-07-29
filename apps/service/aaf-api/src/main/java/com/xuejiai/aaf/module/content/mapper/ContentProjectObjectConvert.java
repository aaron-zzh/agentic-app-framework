package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentProjectObject;
import com.xuejiai.aaf.module.content.vo.ContentProjectObjectCreateDTO;

/**
 * 项目对象对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentProjectObjectConvert {

    ContentProjectObjectConvert INSTANCE = Mappers.getMapper(ContentProjectObjectConvert.class);

    ContentProjectObject toEntity(ContentProjectObjectCreateDTO dto);
}
